/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.logging.application;

import io.opentelemetry.javaagent.bootstrap.InternalLogger;
import io.opentelemetry.javaagent.bootstrap.internal.InTransformation;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;

/**
 * Wraps the application logger factory so that the agent never calls into the application logging
 * system while a class file transformation is in progress on the current thread.
 *
 * <p>Writing a log record from inside a transformation makes the logging framework load classes,
 * and classes loaded in that window are defined without instrumentation - which silently disables
 * the instrumentation of the very framework the agent is logging through. Records produced in that
 * window are queued instead and emitted from a dedicated thread, where the same class loads are
 * instrumented normally.
 */
final class TransformSafeApplicationLoggerFactory implements InternalLogger.Factory {

  private static final int QUEUE_CAPACITY = 1000;

  private final InternalLogger.Factory delegate;
  private final BlockingQueue<DeferredLog> deferredLogs = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
  private final AtomicBoolean drainThreadStarted = new AtomicBoolean();

  TransformSafeApplicationLoggerFactory(InternalLogger.Factory delegate) {
    this.delegate = delegate;
  }

  @Override
  public InternalLogger create(String name) {
    return new TransformSafeLogger(name);
  }

  private void defer(
      String name, InternalLogger.Level level, String message, @Nullable Throwable error) {
    // drop the record if the queue is full, same as the in memory log store does
    if (deferredLogs.offer(new DeferredLog(name, level, message, error))) {
      startDrainThread();
    }
  }

  private void startDrainThread() {
    if (drainThreadStarted.compareAndSet(false, true)) {
      Thread thread = new Thread(this::drain);
      thread.setName("otel-javaagent-application-logger");
      thread.setDaemon(true);
      thread.setContextClassLoader(null);
      thread.start();
    }
  }

  private void drain() {
    try {
      while (true) {
        DeferredLog deferredLog = deferredLogs.take();
        delegate
            .create(deferredLog.name)
            .log(deferredLog.level, deferredLog.message, deferredLog.error);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private final class TransformSafeLogger implements InternalLogger {

    private final String name;
    @Nullable private volatile InternalLogger actual;

    TransformSafeLogger(String name) {
      this.name = name;
    }

    @Override
    public boolean isLoggable(InternalLogger.Level level) {
      if (InTransformation.get()) {
        // the application logging system can't be consulted here; let the caller build the record
        // and decide when it is actually emitted
        return true;
      }
      return actual().isLoggable(level);
    }

    @Override
    public void log(InternalLogger.Level level, String message, @Nullable Throwable error) {
      if (InTransformation.get()) {
        defer(name, level, message, error);
        return;
      }
      actual().log(level, message, error);
    }

    @Override
    public String name() {
      return name;
    }

    // created lazily - instantiating the bridged logger calls into the application logging system,
    // which must not happen while a transformation is in progress
    private InternalLogger actual() {
      InternalLogger logger = actual;
      if (logger == null) {
        logger = delegate.create(name);
        actual = logger;
      }
      return logger;
    }
  }

  private static final class DeferredLog {
    private final String name;
    private final InternalLogger.Level level;
    private final String message;
    @Nullable private final Throwable error;

    DeferredLog(
        String name, InternalLogger.Level level, String message, @Nullable Throwable error) {
      this.name = name;
      this.level = level;
      this.message = message;
      this.error = error;
    }
  }
}
