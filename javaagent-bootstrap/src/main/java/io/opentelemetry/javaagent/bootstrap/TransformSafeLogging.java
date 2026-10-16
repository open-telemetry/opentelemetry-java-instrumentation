/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;

/**
 * Defers logging under Byte Buddy's circularity lock when the testing workaround is enabled.
 *
 * <p>Gradle redirects System.out and can load classes while holding its output locks. Invoking that
 * destination synchronously from class transformation can deadlock against those locks.
 */
final class TransformSafeLogging implements InternalLogger {

  @Nullable private static volatile Dispatcher dispatcher;

  private final InternalLogger delegate;

  TransformSafeLogging(InternalLogger delegate) {
    this.delegate = delegate;
  }

  static synchronized void initialize(BooleanSupplier isCircularityLockHeld) {
    if (dispatcher == null
        && Boolean.getBoolean("otel.javaagent.testing.transform-safe-logging.enabled")) {
      dispatcher = new Dispatcher(isCircularityLockHeld);
    }
  }

  @Override
  public boolean isLoggable(Level level) {
    return delegate.isLoggable(level);
  }

  @Override
  public void log(Level level, String message, @Nullable Throwable error) {
    Dispatcher dispatcher = TransformSafeLogging.dispatcher;
    if (dispatcher == null) {
      delegate.log(level, message, error);
    } else {
      dispatcher.log(delegate, level, message, error);
    }
  }

  @Override
  public String name() {
    return delegate.name();
  }

  private static final class Dispatcher implements Runnable {
    private final BlockingQueue<LogMessage> queue = new ArrayBlockingQueue<>(1000);
    private final AtomicInteger pending = new AtomicInteger();
    private final BooleanSupplier isCircularityLockHeld;

    Dispatcher(BooleanSupplier isCircularityLockHeld) {
      this.isCircularityLockHeld = isCircularityLockHeld;
      // Resolve the queued message class before any transformer can acquire the circularity lock.
      LogMessage.initialize();
      Thread thread = new Thread(this, "otel-javaagent-transform-safe-logger");
      thread.setDaemon(true);
      thread.setContextClassLoader(null);
      thread.start();
    }

    void log(InternalLogger logger, Level level, String message, @Nullable Throwable error) {
      // Include the record currently being delivered, not just the records still in the queue.
      if (isCircularityLockHeld.getAsBoolean() || pending.get() != 0) {
        pending.incrementAndGet();
        if (!queue.offer(new LogMessage(logger, level, message, error))) {
          pending.decrementAndGet();
        }
      } else {
        logger.log(level, message, error);
      }
    }

    @Override
    public void run() {
      try {
        while (true) {
          LogMessage message = queue.take();
          try {
            message.logger.log(message.level, message.message, message.error);
          } finally {
            pending.decrementAndGet();
          }
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private static final class LogMessage {
    private final InternalLogger logger;
    private final Level level;
    private final String message;
    @Nullable private final Throwable error;

    LogMessage(InternalLogger logger, Level level, String message, @Nullable Throwable error) {
      this.logger = logger;
      this.level = level;
      this.message = message;
      this.error = error;
    }

    static void initialize() {}
  }
}
