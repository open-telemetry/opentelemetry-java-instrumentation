/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.logging;

import io.opentelemetry.javaagent.bootstrap.InternalLogger;
import io.opentelemetry.javaagent.tooling.internal.AgentCircularityLock;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * Debug logging that is performed under class file transform needs to use this class, because
 * gradle deadlocks sporadically under the following sequence:
 * <li>Gradle triggers a class to load while it is holding a lock
 * <li>Class file transform occurs (under this lock) and the agent writes to System.out
 * <li>(Because gradle hijacks System.out), gradle is called from inside of the class file transform
 * <li>Gradle tries to grab a different lock during it's implementation of System.out
 */
final class TransformSafeLogger implements InternalLogger {

  private static final BlockingQueue<LogMessage> logMessageQueue;
  private static final Thread loggerThread;

  static {
    logMessageQueue = new ArrayBlockingQueue<>(1024);
    loggerThread = new Thread(new LogMessageQueueReader(logMessageQueue));
    loggerThread.setName("otel-javaagent-transform-safe-logger");
    loggerThread.setDaemon(true);
    loggerThread.setContextClassLoader(null);
    loggerThread.start();
  }

  private final InternalLogger.Factory factory;
  private final String name;
  private final Consumer<LogMessage> queueLogMessage;
  @Nullable private volatile InternalLogger delegate;

  TransformSafeLogger(InternalLogger.Factory factory, String name, boolean enableMessageQueue) {
    this.factory = factory;
    this.name = name;
    this.queueLogMessage = enableMessageQueue ? logMessageQueue::offer : message -> {};
    if (!AgentCircularityLock.INSTANCE.isHeldByCurrentThread()) {
      // initialize eagerly if transformation isn't in progress
      delegate();
    }
  }

  // The factory's create() may run while the circularity lock is held.
  // Defer application logger creation until log() or isLoggable() passes the lock
  // check, since creation itself can load classes that would miss instrumentation.
  private InternalLogger delegate() {
    InternalLogger logger = delegate;
    if (logger == null) {
      logger = factory.create(name);
      delegate = logger;
    }
    return logger;
  }

  @Override
  public boolean isLoggable(Level level) {
    if (AgentCircularityLock.INSTANCE.isHeldByCurrentThread()
        && (Thread.currentThread() == loggerThread || delegate == null)) {
      // class is being transformed on the logger thread, avoid calling into underlying logger
      // as it could trigger loading more classes which wouldn't get transformed
      return true;
    }
    // the assumption is that calling isLoggable is safe once the underlying logger has been created
    return delegate().isLoggable(level);
  }

  @Override
  public void log(Level level, String message, @Nullable Throwable error) {
    if (AgentCircularityLock.INSTANCE.isHeldByCurrentThread()) {
      queueLogMessage.accept(new LogMessage(level, this::delegate, message, error));
      return;
    }
    delegate().log(level, message, error);
  }

  @Override
  public String name() {
    return name;
  }

  private static class LogMessageQueueReader implements Runnable {
    private final BlockingQueue<LogMessage> queue;

    LogMessageQueueReader(BlockingQueue<LogMessage> queue) {
      this.queue = queue;
    }

    @SuppressWarnings("SystemOut")
    @Override
    public void run() {
      while (true) {
        try {
          LogMessage logMessage = queue.take();
          logMessage.logger.get().log(logMessage.level, logMessage.message, logMessage.error);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        } catch (Throwable throwable) {
          System.out.println("Failed to log message");
          throwable.printStackTrace();
        }
      }
    }
  }

  private static class LogMessage {
    private final Level level;
    private final Supplier<InternalLogger> logger;
    private final String message;
    @Nullable private final Throwable error;

    private LogMessage(
        Level level, Supplier<InternalLogger> logger, String message, @Nullable Throwable error) {
      this.level = level;
      this.logger = logger;
      this.message = message;
      this.error = error;
    }
  }
}
