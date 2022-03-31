/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import java.text.MessageFormat;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Debug logging that is performed under class file transform needs to use this class, because
 * gradle deadlocks sporadically under the following sequence:
 * <li>Gradle triggers a class to load while it is holding a lock
 * <li>Class file transform occurs (under this lock) and the agent writes to System.out
 * <li>(Because gradle hijacks System.out), gradle is called from inside of the class file transform
 * <li>Gradle tries to grab a different lock during it's implementation of System.out
 */
public final class TransformSafeLogger {

  private static final boolean ENABLE_TRANSFORM_SAFE_LOGGING =
      Boolean.getBoolean("otel.javaagent.testing.transform-safe-logging.enabled");

  @Nullable private static final BlockingQueue<LogMessage> logMessageQueue;

  static {
    if (ENABLE_TRANSFORM_SAFE_LOGGING) {
      logMessageQueue = new ArrayBlockingQueue<>(1000);
      Thread thread = new Thread(new LogMessageQueueReader());
      thread.setName("otel-javaagent-transform-safe-logger");
      thread.setDaemon(true);
      thread.start();
    } else {
      logMessageQueue = null;
    }
  }

  private final Logger logger;

  public static TransformSafeLogger getLogger(Class<?> clazz) {
    return new TransformSafeLogger(Logger.getLogger(clazz.getName()));
  }

  private TransformSafeLogger(Logger logger) {
    this.logger = logger;
  }

  public boolean isLoggable(Level level) {
    return logger.isLoggable(level);
  }

  public void log(Level level, String message) {
    if (logMessageQueue != null) {
      logMessageQueue.offer(new LogMessage(level, logger, message, null, null));
    } else {
      logger.log(level, message);
    }
  }

  public void log(Level level, String message, Object arg) {
    if (logMessageQueue != null) {
      logMessageQueue.offer(new LogMessage(level, logger, message, null, new Object[] {arg}));
    } else {
      logger.log(level, message, arg);
    }
  }

  public void log(Level level, String message, Object[] args) {
    if (logMessageQueue != null) {
      logMessageQueue.offer(new LogMessage(level, logger, message, null, args));
    } else {
      logger.log(level, message, args);
    }
  }

  public void log(Level level, String message, Object[] args, Throwable error) {
    if (logMessageQueue != null) {
      logMessageQueue.offer(new LogMessage(level, logger, message, error, args));
    } else {
      logger.log(level, formatMessage(message, args), error);
    }
  }

  private static class LogMessageQueueReader implements Runnable {
    @Override
    public void run() {
      try {
        while (true) {
          LogMessage logMessage = logMessageQueue.take();
          logMessage.logger.log(
              logMessage.level,
              formatMessage(logMessage.format, logMessage.arguments),
              logMessage.error);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private static class LogMessage {
    private final Level level;
    private final Logger logger;
    private final String format;
    private final Throwable error;
    private final Object[] arguments;

    private LogMessage(
        Level level, Logger logger, String format, Throwable error, Object[] arguments) {
      this.level = level;
      this.logger = logger;
      this.format = format;
      this.error = error;
      this.arguments = arguments;
    }
  }

  private static String formatMessage(String format, Object[] arguments) {
    if (arguments == null || arguments.length == 0) {
      return format;
    } else {
      return MessageFormat.format(format, arguments);
    }
  }
}
