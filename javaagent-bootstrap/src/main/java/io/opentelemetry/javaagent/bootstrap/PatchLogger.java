/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import java.text.MessageFormat;
import java.util.ResourceBundle;
import java.util.function.Supplier;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Dependencies of the agent sometimes call java.util.logging.Logger.getLogger(). This can have the
 * effect of initializing the global LogManager incompatibly with the user's app.
 *
 * <p>Shadow rewrites will redirect those calls to this class, which will return a safe PatchLogger.
 *
 * <p>This also has the desired outcome of redirecting all logging to a single destination, as
 * configured by the {@code LoggingCustomizer} implementation.
 */
public class PatchLogger {

  public static final String GLOBAL_LOGGER_NAME = "global";

  public static final PatchLogger global = new PatchLogger(GLOBAL_LOGGER_NAME);

  private final InternalLogger internalLogger;

  private ResourceBundle resourceBundle;

  public static PatchLogger getLogger(String name) {
    return new PatchLogger(name);
  }

  public static PatchLogger getLogger(String name, String resourceBundleName) {
    return new PatchLogger(name);
  }

  private PatchLogger(String name) {
    this(InternalLogger.getLogger(name));
  }

  // visible for testing
  PatchLogger(InternalLogger internalLogger) {
    this.internalLogger = internalLogger;
  }

  public String getName() {
    return internalLogger.name();
  }

  public void severe(String msg) {
    internalLogger.log(InternalLogger.Level.ERROR, msg, null);
  }

  public void severe(Supplier<String> msgSupplier) {
    if (internalLogger.isLoggable(InternalLogger.Level.ERROR)) {
      internalLogger.log(InternalLogger.Level.ERROR, msgSupplier.get(), null);
    }
  }

  public void warning(String msg) {
    internalLogger.log(InternalLogger.Level.WARN, msg, null);
  }

  public void warning(Supplier<String> msgSupplier) {
    if (internalLogger.isLoggable(InternalLogger.Level.WARN)) {
      internalLogger.log(InternalLogger.Level.WARN, msgSupplier.get(), null);
    }
  }

  public void info(String msg) {
    internalLogger.log(InternalLogger.Level.INFO, msg, null);
  }

  public void info(Supplier<String> msgSupplier) {
    if (internalLogger.isLoggable(InternalLogger.Level.INFO)) {
      internalLogger.log(InternalLogger.Level.INFO, msgSupplier.get(), null);
    }
  }

  public void config(String msg) {
    info(msg);
  }

  public void config(Supplier<String> msgSupplier) {
    info(msgSupplier);
  }

  public void fine(String msg) {
    internalLogger.log(InternalLogger.Level.DEBUG, msg, null);
  }

  public void fine(Supplier<String> msgSupplier) {
    if (internalLogger.isLoggable(InternalLogger.Level.DEBUG)) {
      internalLogger.log(InternalLogger.Level.DEBUG, msgSupplier.get(), null);
    }
  }

  public void finer(String msg) {
    internalLogger.log(InternalLogger.Level.TRACE, msg, null);
  }

  public void finer(Supplier<String> msgSupplier) {
    if (internalLogger.isLoggable(InternalLogger.Level.TRACE)) {
      internalLogger.log(InternalLogger.Level.TRACE, msgSupplier.get(), null);
    }
  }

  public void finest(String msg) {
    finer(msg);
  }

  public void finest(Supplier<String> msgSupplier) {
    finer(msgSupplier);
  }

  public void log(LogRecord record) {
    InternalLogger.Level internalLevel = toInternalLevel(record.getLevel());
    if (internalLogger.isLoggable(internalLevel)) {
      internalLogger.log(internalLevel, getMessage(record), record.getThrown());
    }
  }

  public void log(Level level, String msg) {
    internalLogger.log(toInternalLevel(level), msg, null);
  }

  public void log(Level level, String msg, Object param1) {
    InternalLogger.Level internalLevel = toInternalLevel(level);
    if (internalLogger.isLoggable(internalLevel)) {
      internalLogger.log(internalLevel, MessageFormat.format(msg, param1), null);
    }
  }

  public void log(Level level, String msg, Object[] params) {
    InternalLogger.Level internalLevel = toInternalLevel(level);
    if (internalLogger.isLoggable(internalLevel)) {
      internalLogger.log(internalLevel, MessageFormat.format(msg, params), null);
    }
  }

  public void log(Level level, String msg, Throwable thrown) {
    internalLogger.log(toInternalLevel(level), msg, thrown);
  }

  public void log(Level level, Supplier<String> msgSupplier) {
    InternalLogger.Level internalLevel = toInternalLevel(level);
    if (internalLogger.isLoggable(internalLevel)) {
      internalLogger.log(internalLevel, msgSupplier.get(), null);
    }
  }

  public void log(Level level, Throwable thrown, Supplier<String> msgSupplier) {
    InternalLogger.Level internalLevel = toInternalLevel(level);
    if (internalLogger.isLoggable(internalLevel)) {
      internalLogger.log(internalLevel, msgSupplier.get(), thrown);
    }
  }

  public boolean isLoggable(Level level) {
    return internalLogger.isLoggable(toInternalLevel(level));
  }

  public Level getLevel() {
    if (internalLogger.isLoggable(InternalLogger.Level.ERROR)) {
      return Level.SEVERE;
    } else if (internalLogger.isLoggable(InternalLogger.Level.WARN)) {
      return Level.WARNING;
    } else if (internalLogger.isLoggable(InternalLogger.Level.INFO)) {
      return Level.CONFIG;
    } else if (internalLogger.isLoggable(InternalLogger.Level.DEBUG)) {
      return Level.FINE;
    } else if (internalLogger.isLoggable(InternalLogger.Level.TRACE)) {
      return Level.FINEST;
    } else {
      return Level.OFF;
    }
  }

  private static InternalLogger.Level toInternalLevel(Level level) {
    if (level.intValue() >= Level.SEVERE.intValue()) {
      return InternalLogger.Level.ERROR;
    } else if (level.intValue() >= Level.WARNING.intValue()) {
      return InternalLogger.Level.WARN;
    } else if (level.intValue() >= Level.CONFIG.intValue()) {
      return InternalLogger.Level.INFO;
    } else if (level.intValue() >= Level.FINE.intValue()) {
      return InternalLogger.Level.DEBUG;
    } else {
      return InternalLogger.Level.TRACE;
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

  public void logp(
      Level level, String sourceClass, String sourceMethod, Supplier<String> msgSupplier) {
    log(level, msgSupplier);
  }

  public void logp(
      Level level,
      String sourceClass,
      String sourceMethod,
      Throwable thrown,
      Supplier<String> msgSupplier) {
    log(level, thrown, msgSupplier);
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

  public void removeHandler(Handler handler) {}

  public static PatchLogger getAnonymousLogger() {
    return getLogger("");
  }

  public static PatchLogger getAnonymousLogger(String resourceBundleName) {
    return getLogger("");
  }

  public static PatchLogger getGlobal() {
    return global;
  }

  public static void setFilter(Filter filter) {}

  public static Filter getFilter() {
    return null;
  }

  public static void setUseParentHandlers(boolean useParentHandlers) {}

  public static boolean getUseParentHandlers() {
    return true;
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
