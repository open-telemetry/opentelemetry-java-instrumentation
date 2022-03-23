/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINEST;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;

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

  public void fine(String format, Object arg) {
    if (logMessageQueue != null) {
      logMessageQueue.offer(new LogMessage(FINE, logger, format, null, arg));
    } else {
      logger.log(FINE, format, arg);
    }
  }

  public void fine(String format, Object arg1, Object arg2) {
    if (logMessageQueue != null) {
      logMessageQueue.offer(new LogMessage(FINE, logger, format, null, arg1, arg2));
    } else {
      logger.log(FINE, format, new Object[] {arg1, arg2});
    }
  }

  public void fine(String format, Object... arguments) {
    if (logMessageQueue != null) {
      logMessageQueue.offer(new LogMessage(FINE, logger, format, null, arguments));
    } else {
      logger.log(FINE, format, arguments);
    }
  }

  public void fine(Throwable error, String format, Object... arguments) {
    if (logMessageQueue != null) {
      logMessageQueue.offer(new LogMessage(FINE, logger, format, error, arguments));
    } else {
      logger.log(FINE, formatMessage(format, arguments), error);
    }
  }

  public boolean isFineLoggable() {
    return logger.isLoggable(FINE);
  }

  public void finest(String format, Object arg) {
    if (logMessageQueue != null) {
      logMessageQueue.offer(new LogMessage(FINEST, logger, format, null, arg));
    } else {
      logger.log(FINEST, format, arg);
    }
  }

  public void finest(String format, Object arg1, Object arg2) {
    if (logMessageQueue != null) {
      logMessageQueue.offer(new LogMessage(FINEST, logger, format, null, arg1, arg2));
    } else {
      logger.log(FINEST, format, new Object[] {arg1, arg2});
    }
  }

  public void finest(String format, Object... arguments) {
    if (logMessageQueue != null) {
      logMessageQueue.offer(new LogMessage(FINEST, logger, format, null, arguments));
    } else {
      logger.log(FINEST, format, arguments);
    }
  }

  public boolean isFinestLoggable() {
    return logger.isLoggable(FINEST);
  }

  public void warning(String format) {
    if (logMessageQueue != null) {
      logMessageQueue.offer(new LogMessage(WARNING, logger, format, null));
    } else {
      logger.warning(format);
    }
  }

  public void warning(String format, Object arg) {
    if (logMessageQueue != null) {
      logMessageQueue.offer(new LogMessage(WARNING, logger, format, null, arg));
    } else {
      logger.log(WARNING, format, arg);
    }
  }

  public void warning(String format, Object arg1, Object arg2) {
    if (logMessageQueue != null) {
      logMessageQueue.offer(new LogMessage(WARNING, logger, format, null, arg1, arg2));
    } else {
      logger.log(WARNING, format, new Object[] {arg1, arg2});
    }
  }

  public void warning(String format, Object... arguments) {
    if (logMessageQueue != null) {
      logMessageQueue.offer(new LogMessage(WARNING, logger, format, null, arguments));
    } else {
      logger.log(WARNING, format, arguments);
    }
  }

  public void severe(String format) {
    if (logMessageQueue != null) {
      logMessageQueue.offer(new LogMessage(SEVERE, logger, format, null));
    } else {
      logger.severe(format);
    }
  }

  public void severe(String format, Object arg) {
    if (logMessageQueue != null) {
      logMessageQueue.offer(new LogMessage(SEVERE, logger, format, null, arg));
    } else {
      logger.log(SEVERE, format, arg);
    }
  }

  public void severe(String format, Object arg1, Object arg2) {
    if (logMessageQueue != null) {
      logMessageQueue.offer(new LogMessage(SEVERE, logger, format, null, arg1, arg2));
    } else {
      logger.log(SEVERE, format, new Object[] {arg1, arg2});
    }
  }

  public void severe(String format, Object... arguments) {
    if (logMessageQueue != null) {
      logMessageQueue.offer(new LogMessage(SEVERE, logger, format, null, arguments));
    } else {
      logger.log(SEVERE, format, arguments);
    }
  }

  public void severe(Throwable error, String format, Object... arguments) {
    if (logMessageQueue != null) {
      logMessageQueue.offer(new LogMessage(SEVERE, logger, format, error, arguments));
    } else {
      logger.log(SEVERE, formatMessage(format, arguments), error);
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
        Level level, Logger logger, String format, Throwable error, Object... arguments) {
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
