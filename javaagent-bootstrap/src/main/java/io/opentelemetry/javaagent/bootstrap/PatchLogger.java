/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import java.text.MessageFormat;
import java.util.ResourceBundle;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Dependencies of the agent sometimes call java.util.logging.Logger.getLogger(). This can have the
 * effect of initializing the global LogManager incompatibly with the user's app.
 *
 * <p>Shadow rewrites will redirect those calls to this class, which will return a safe PatchLogger.
 *
 * <p>This also has the desired outcome of redirecting all logging to a single destination (SLF4J).
 */
public class PatchLogger {

  public static final String GLOBAL_LOGGER_NAME = "global";

  public static final PatchLogger global = new PatchLogger(GLOBAL_LOGGER_NAME);

  private final org.slf4j.Logger slf4jLogger;

  private ResourceBundle resourceBundle;

  public static PatchLogger getLogger(String name) {
    return new PatchLogger(name);
  }

  public static PatchLogger getLogger(String name, String resourceBundleName) {
    return new PatchLogger(name);
  }

  private PatchLogger(String name) {
    this(org.slf4j.LoggerFactory.getLogger(name));
  }

  // visible for testing
  PatchLogger(org.slf4j.Logger logger) {
    slf4jLogger = logger;
  }

  // visible for testing
  org.slf4j.Logger getSlf4jLogger() {
    return slf4jLogger;
  }

  public String getName() {
    return slf4jLogger.getName();
  }

  public void severe(String msg) {
    slf4jLogger.error(msg);
  }

  public void warning(String msg) {
    slf4jLogger.warn(msg);
  }

  public void info(String msg) {
    slf4jLogger.info(msg);
  }

  public void config(String msg) {
    slf4jLogger.info(msg);
  }

  public void fine(String msg) {
    slf4jLogger.debug(msg);
  }

  public void finer(String msg) {
    slf4jLogger.trace(msg);
  }

  public void finest(String msg) {
    slf4jLogger.trace(msg);
  }

  public void log(LogRecord record) {
    Level level = record.getLevel();
    if (level.intValue() >= Level.SEVERE.intValue()) {
      if (slf4jLogger.isErrorEnabled()) {
        slf4jLogger.error(getMessage(record), record.getThrown());
      }
    } else if (level.intValue() >= Level.WARNING.intValue()) {
      if (slf4jLogger.isWarnEnabled()) {
        slf4jLogger.warn(getMessage(record), record.getThrown());
      }
    } else if (level.intValue() >= Level.CONFIG.intValue()) {
      if (slf4jLogger.isInfoEnabled()) {
        slf4jLogger.info(getMessage(record), record.getThrown());
      }
    } else if (level.intValue() >= Level.FINE.intValue()) {
      if (slf4jLogger.isDebugEnabled()) {
        slf4jLogger.debug(getMessage(record), record.getThrown());
      }
    } else {
      if (slf4jLogger.isTraceEnabled()) {
        slf4jLogger.trace(getMessage(record), record.getThrown());
      }
    }
  }

  public void log(Level level, String msg) {
    if (level.intValue() >= Level.SEVERE.intValue()) {
      slf4jLogger.error(msg);
    } else if (level.intValue() >= Level.WARNING.intValue()) {
      slf4jLogger.warn(msg);
    } else if (level.intValue() >= Level.CONFIG.intValue()) {
      slf4jLogger.info(msg);
    } else if (level.intValue() >= Level.FINE.intValue()) {
      slf4jLogger.debug(msg);
    } else {
      slf4jLogger.trace(msg);
    }
  }

  public void log(Level level, String msg, Object param1) {
    if (level.intValue() >= Level.SEVERE.intValue()) {
      if (slf4jLogger.isErrorEnabled()) {
        slf4jLogger.error(MessageFormat.format(msg, param1));
      }
    } else if (level.intValue() >= Level.WARNING.intValue()) {
      if (slf4jLogger.isWarnEnabled()) {
        slf4jLogger.warn(MessageFormat.format(msg, param1));
      }
    } else if (level.intValue() >= Level.CONFIG.intValue()) {
      if (slf4jLogger.isInfoEnabled()) {
        slf4jLogger.info(MessageFormat.format(msg, param1));
      }
    } else if (level.intValue() >= Level.FINE.intValue()) {
      if (slf4jLogger.isDebugEnabled()) {
        slf4jLogger.debug(MessageFormat.format(msg, param1));
      }
    } else {
      if (slf4jLogger.isTraceEnabled()) {
        slf4jLogger.trace(MessageFormat.format(msg, param1));
      }
    }
  }

  public void log(Level level, String msg, Object[] params) {
    if (level.intValue() >= Level.SEVERE.intValue()) {
      if (slf4jLogger.isErrorEnabled()) {
        slf4jLogger.error(MessageFormat.format(msg, params));
      }
    } else if (level.intValue() >= Level.WARNING.intValue()) {
      if (slf4jLogger.isWarnEnabled()) {
        slf4jLogger.warn(MessageFormat.format(msg, params));
      }
    } else if (level.intValue() >= Level.CONFIG.intValue()) {
      if (slf4jLogger.isInfoEnabled()) {
        slf4jLogger.info(MessageFormat.format(msg, params));
      }
    } else if (level.intValue() >= Level.FINE.intValue()) {
      if (slf4jLogger.isDebugEnabled()) {
        slf4jLogger.debug(MessageFormat.format(msg, params));
      }
    } else {
      if (slf4jLogger.isTraceEnabled()) {
        slf4jLogger.trace(MessageFormat.format(msg, params));
      }
    }
  }

  public void log(Level level, String msg, Throwable thrown) {
    if (level.intValue() >= Level.SEVERE.intValue()) {
      slf4jLogger.error(msg, thrown);
    } else if (level.intValue() >= Level.WARNING.intValue()) {
      slf4jLogger.warn(msg, thrown);
    } else if (level.intValue() >= Level.CONFIG.intValue()) {
      slf4jLogger.info(msg, thrown);
    } else if (level.intValue() >= Level.FINE.intValue()) {
      slf4jLogger.debug(msg, thrown);
    } else {
      slf4jLogger.trace(msg, thrown);
    }
  }

  public boolean isLoggable(Level level) {
    if (level.intValue() >= Level.SEVERE.intValue()) {
      return slf4jLogger.isErrorEnabled();
    } else if (level.intValue() >= Level.WARNING.intValue()) {
      return slf4jLogger.isWarnEnabled();
    } else if (level.intValue() >= Level.CONFIG.intValue()) {
      return slf4jLogger.isInfoEnabled();
    } else if (level.intValue() >= Level.FINE.intValue()) {
      return slf4jLogger.isDebugEnabled();
    } else {
      return slf4jLogger.isTraceEnabled();
    }
  }

  public Level getLevel() {
    if (slf4jLogger.isErrorEnabled()) {
      return Level.SEVERE;
    } else if (slf4jLogger.isWarnEnabled()) {
      return Level.WARNING;
    } else if (slf4jLogger.isInfoEnabled()) {
      return Level.CONFIG;
    } else if (slf4jLogger.isDebugEnabled()) {
      return Level.FINE;
    } else if (slf4jLogger.isTraceEnabled()) {
      return Level.FINEST;
    } else {
      return Level.OFF;
    }
  }

  public void logp(Level level, String sourceClass, String sourceMethod, String msg) {
    log(level, msg);
  }

  public void logp(
      Level level, String sourceClass, String sourceMethod, String msg, Object param1) {
    log(level, msg, param1);
  }

  public void logp(
      Level level, String sourceClass, String sourceMethod, String msg, Object[] params) {
    log(level, msg, params);
  }

  public void logp(
      Level level, String sourceClass, String sourceMethod, String msg, Throwable thrown) {
    log(level, msg, thrown);
  }

  public void logrb(
      Level level, String sourceClass, String sourceMethod, String bundleName, String msg) {
    log(level, msg);
  }

  public void logrb(
      Level level,
      String sourceClass,
      String sourceMethod,
      String bundleName,
      String msg,
      Object param1) {
    log(level, msg, param1);
  }

  public void logrb(
      Level level,
      String sourceClass,
      String sourceMethod,
      String bundleName,
      String msg,
      Object[] params) {
    log(level, msg, params);
  }

  public void logrb(
      Level level,
      String sourceClass,
      String sourceMethod,
      ResourceBundle bundle,
      String msg,
      Object... params) {
    log(level, msg, params);
  }

  public void logrb(Level level, ResourceBundle bundle, String msg, Object... params) {
    log(level, msg, params);
  }

  public void logrb(
      Level level,
      String sourceClass,
      String sourceMethod,
      String bundleName,
      String msg,
      Throwable thrown) {
    log(level, msg, thrown);
  }

  public void logrb(
      Level level,
      String sourceClass,
      String sourceMethod,
      ResourceBundle bundle,
      String msg,
      Throwable thrown) {
    log(level, msg, thrown);
  }

  public void logrb(Level level, ResourceBundle bundle, String msg, Throwable thrown) {
    log(level, msg, thrown);
  }

  public void entering(String sourceClass, String sourceMethod) {}

  public void entering(String sourceClass, String sourceMethod, Object param1) {}

  public void entering(String sourceClass, String sourceMethod, Object[] params) {}

  public void exiting(String sourceClass, String sourceMethod) {}

  public void exiting(String sourceClass, String sourceMethod, Object result) {}

  public void throwing(String sourceClass, String sourceMethod, Throwable thrown) {}

  public ResourceBundle getResourceBundle() {
    return resourceBundle;
  }

  public void setResourceBundle(ResourceBundle resourceBundle) {
    this.resourceBundle = resourceBundle;
  }

  public String getResourceBundleName() {
    return null;
  }

  public PatchLogger getParent() {
    return getLogger("");
  }

  public void setParent(PatchLogger parent) {}

  public void setLevel(Level newLevel) {}

  public Handler[] getHandlers() {
    return new Handler[0];
  }

  public void addHandler(Handler handler) {}

  public static PatchLogger getAnonymousLogger() {
    return getLogger("");
  }

  public static PatchLogger getAnonymousLogger(String resourceBundleName) {
    return getLogger("");
  }

  public static final PatchLogger getGlobal() {
    return global;
  }

  private static String getMessage(LogRecord record) {
    String msg = record.getMessage();
    Object[] params = record.getParameters();
    if (params == null) {
      return msg;
    } else {
      return MessageFormat.format(msg, params);
    }
  }
}
