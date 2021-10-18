/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

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
    return new TransformSafeLogger(LoggerFactory.getLogger(clazz));
  }

  private TransformSafeLogger(Logger logger) {
    this.logger = logger;
  }

  public void debug(String format, Object arg) {
    if (logMessageQueue != null) {
      logMessageQueue.offer(new LogMessage(Level.DEBUG, logger, format, arg));
    } else {
      logger.debug(format, arg);
    }
  }

  public void debug(String format, Object arg1, Object arg2) {
    if (logMessageQueue != null) {
      logMessageQueue.offer(new LogMessage(Level.DEBUG, logger, format, arg1, arg2));
    } else {
      logger.debug(format, arg1, arg2);
    }
  }

  public void debug(String format, Object... arguments) {
    if (logMessageQueue != null) {
      logMessageQueue.offer(new LogMessage(Level.DEBUG, logger, format, arguments));
    } else {
      logger.debug(format, arguments);
    }
  }

  public boolean isDebugEnabled() {
    return logger.isDebugEnabled();
  }

  public void trace(String format, Object arg) {
    if (logMessageQueue != null) {
      logMessageQueue.offer(new LogMessage(Level.TRACE, logger, format, arg));
    } else {
      logger.trace(format, arg);
    }
  }

  public void trace(String format, Object arg1, Object arg2) {
    if (logMessageQueue != null) {
      logMessageQueue.offer(new LogMessage(Level.TRACE, logger, format, arg1, arg2));
    } else {
      logger.trace(format, arg1, arg2);
    }
  }

  public void trace(String format, Object... arguments) {
    if (logMessageQueue != null) {
      logMessageQueue.offer(new LogMessage(Level.TRACE, logger, format, arguments));
    } else {
      logger.trace(format, arguments);
    }
  }

  public void warn(String format) {
    if (logMessageQueue != null) {
      logMessageQueue.offer(new LogMessage(Level.WARN, logger, format));
    } else {
      logger.warn(format);
    }
  }

  public void warn(String format, Object arg) {
    if (logMessageQueue != null) {
      logMessageQueue.offer(new LogMessage(Level.WARN, logger, format, arg));
    } else {
      logger.warn(format, arg);
    }
  }

  public void warn(String format, Object arg1, Object arg2) {
    if (logMessageQueue != null) {
      logMessageQueue.offer(new LogMessage(Level.WARN, logger, format, arg1, arg2));
    } else {
      logger.warn(format, arg1, arg2);
    }
  }

  public void warn(String format, Object... arguments) {
    if (logMessageQueue != null) {
      logMessageQueue.offer(new LogMessage(Level.WARN, logger, format, arguments));
    } else {
      logger.warn(format, arguments);
    }
  }

  public void error(String format) {
    if (logMessageQueue != null) {
      logMessageQueue.offer(new LogMessage(Level.ERROR, logger, format));
    } else {
      logger.error(format);
    }
  }

  public void error(String format, Object arg) {
    if (logMessageQueue != null) {
      logMessageQueue.offer(new LogMessage(Level.ERROR, logger, format, arg));
    } else {
      logger.error(format, arg);
    }
  }

  public void error(String format, Object arg1, Object arg2) {
    if (logMessageQueue != null) {
      logMessageQueue.offer(new LogMessage(Level.ERROR, logger, format, arg1, arg2));
    } else {
      logger.error(format, arg1, arg2);
    }
  }

  public void error(String format, Object... arguments) {
    if (logMessageQueue != null) {
      logMessageQueue.offer(new LogMessage(Level.ERROR, logger, format, arguments));
    } else {
      logger.error(format, arguments);
    }
  }

  public boolean isTraceEnabled() {
    return logger.isTraceEnabled();
  }

  private static class LogMessageQueueReader implements Runnable {
    @Override
    public void run() {
      try {
        while (true) {
          LogMessage logMessage = logMessageQueue.take();
          if (logMessage.level == Level.DEBUG) {
            logMessage.logger.debug(logMessage.format, logMessage.arguments);
          } else if (logMessage.level == Level.TRACE) {
            logMessage.logger.trace(logMessage.format, logMessage.arguments);
          } else {
            logMessage.logger.warn(
                "level {} not implemented yet in TransformSafeLogger", logMessage.level);
          }
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
    private final Object[] arguments;

    private LogMessage(Level level, Logger logger, String format, Object... arguments) {
      this.level = level;
      this.logger = logger;
      this.format = format;
      this.arguments = arguments;
    }
  }
}
