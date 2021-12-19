/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package application.java.util.logging;

import java.util.ResourceBundle;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

// java.util.logging.Logger shaded so that it can be used in instrumentation
// of java.util.logging.Logger itself, and then its usage can be unshaded
// after java.util.logging.Logger is shaded to the "PatchLogger"
@SuppressWarnings("unused")
public class Logger {

  public static final String GLOBAL_LOGGER_NAME = "global";

  public static Logger getLogger(String name) {

    throw new UnsupportedOperationException();
  }

  public static Logger getLogger(String name, String resourceBundleName) {
    throw new UnsupportedOperationException();
  }

  public String getName() {
    throw new UnsupportedOperationException();
  }

  public void severe(String msg) {
    throw new UnsupportedOperationException();
  }

  public void warning(String msg) {
    throw new UnsupportedOperationException();
  }

  public void info(String msg) {
    throw new UnsupportedOperationException();
  }

  public void config(String msg) {
    throw new UnsupportedOperationException();
  }

  public void fine(String msg) {
    throw new UnsupportedOperationException();
  }

  public void finer(String msg) {
    throw new UnsupportedOperationException();
  }

  public void finest(String msg) {
    throw new UnsupportedOperationException();
  }

  public void log(LogRecord record) {
    throw new UnsupportedOperationException();
  }

  public void log(Level level, String msg) {
    throw new UnsupportedOperationException();
  }

  public void log(Level level, String msg, Object param1) {
    throw new UnsupportedOperationException();
  }

  public void log(Level level, String msg, Object[] params) {
    throw new UnsupportedOperationException();
  }

  public void log(Level level, String msg, Throwable thrown) {
    throw new UnsupportedOperationException();
  }

  public boolean isLoggable(Level level) {
    throw new UnsupportedOperationException();
  }

  public Level getLevel() {
    throw new UnsupportedOperationException();
  }

  public void logp(Level level, String sourceClass, String sourceMethod, String msg) {
    throw new UnsupportedOperationException();
  }

  public void logp(
      Level level, String sourceClass, String sourceMethod, String msg, Object param1) {
    throw new UnsupportedOperationException();
  }

  public void logp(
      Level level, String sourceClass, String sourceMethod, String msg, Object[] params) {
    throw new UnsupportedOperationException();
  }

  public void logp(
      Level level, String sourceClass, String sourceMethod, String msg, Throwable thrown) {
    throw new UnsupportedOperationException();
  }

  public void logrb(
      Level level, String sourceClass, String sourceMethod, String bundleName, String msg) {
    throw new UnsupportedOperationException();
  }

  public void logrb(
      Level level,
      String sourceClass,
      String sourceMethod,
      String bundleName,
      String msg,
      Object param1) {
    throw new UnsupportedOperationException();
  }

  public void logrb(
      Level level,
      String sourceClass,
      String sourceMethod,
      String bundleName,
      String msg,
      Object[] params) {
    throw new UnsupportedOperationException();
  }

  public void logrb(
      Level level,
      String sourceClass,
      String sourceMethod,
      ResourceBundle bundle,
      String msg,
      Object... params) {
    throw new UnsupportedOperationException();
  }

  public void logrb(Level level, ResourceBundle bundle, String msg, Object... params) {
    throw new UnsupportedOperationException();
  }

  public void logrb(
      Level level,
      String sourceClass,
      String sourceMethod,
      String bundleName,
      String msg,
      Throwable thrown) {
    throw new UnsupportedOperationException();
  }

  public void logrb(
      Level level,
      String sourceClass,
      String sourceMethod,
      ResourceBundle bundle,
      String msg,
      Throwable thrown) {
    throw new UnsupportedOperationException();
  }

  public void logrb(Level level, ResourceBundle bundle, String msg, Throwable thrown) {
    throw new UnsupportedOperationException();
  }

  public void entering(String sourceClass, String sourceMethod) {
    throw new UnsupportedOperationException();
  }

  public void entering(String sourceClass, String sourceMethod, Object param1) {
    throw new UnsupportedOperationException();
  }

  public void entering(String sourceClass, String sourceMethod, Object[] params) {
    throw new UnsupportedOperationException();
  }

  public void exiting(String sourceClass, String sourceMethod) {
    throw new UnsupportedOperationException();
  }

  public void exiting(String sourceClass, String sourceMethod, Object result) {
    throw new UnsupportedOperationException();
  }

  public void throwing(String sourceClass, String sourceMethod, Throwable thrown) {
    throw new UnsupportedOperationException();
  }

  public ResourceBundle getResourceBundle() {
    throw new UnsupportedOperationException();
  }

  public void setResourceBundle(ResourceBundle resourceBundle) {
    throw new UnsupportedOperationException();
  }

  public String getResourceBundleName() {
    throw new UnsupportedOperationException();
  }

  public Logger getParent() {
    throw new UnsupportedOperationException();
  }

  public void setParent(Logger parent) {
    throw new UnsupportedOperationException();
  }

  public void setLevel(Level newLevel) {
    throw new UnsupportedOperationException();
  }

  public Handler[] getHandlers() {
    throw new UnsupportedOperationException();
  }

  public void addHandler(Handler handler) {
    throw new UnsupportedOperationException();
  }

  public static Logger getAnonymousLogger() {
    throw new UnsupportedOperationException();
  }

  public static Logger getAnonymousLogger(String resourceBundleName) {
    throw new UnsupportedOperationException();
  }

  public static Logger getGlobal() {
    throw new UnsupportedOperationException();
  }
}
