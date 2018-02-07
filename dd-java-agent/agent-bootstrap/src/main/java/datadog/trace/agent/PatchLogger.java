package datadog.trace.agent;

import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Dependencies of the agent somtimes call Logger.getLogger This can have the effect of initializing
 * the global logger incompatibly with the user's app.
 *
 * <p>Shadow rewrites will redirect those calls to this class, which will return a safe logger.
 */
public class PatchLogger {
  // FIXME: Use "datadog.trace.agent.bootstrap" package name for clarity
  private static final PatchLogger SAFE_LOGGER = new PatchLogger("datadogSafeLogger", "bundle");

  public static PatchLogger getLogger(String name) {
    return SAFE_LOGGER;
  }

  public static PatchLogger getLogger(String name, String resourceBundleName) {
    return SAFE_LOGGER;
  }

  public static PatchLogger getAnonymousLogger() {
    return SAFE_LOGGER;
  }

  public static PatchLogger getAnonymousLogger(String resourceBundleName) {
    return SAFE_LOGGER;
  }

  protected PatchLogger(String name, String resourceBundleName) {
    // super(name, resourceBundleName);
  }

  // providing a bunch of empty log methods

  public void log(LogRecord record) {}

  public void log(Level level, String msg) {}

  public void log(Level level, String msg, Object param1) {}

  public void log(Level level, String msg, Object params[]) {}

  public void log(Level level, String msg, Throwable thrown) {}

  public void logp(Level level, String sourceClass, String sourceMethod, String msg) {}

  public void logp(
      Level level, String sourceClass, String sourceMethod, String msg, Object param1) {}

  public void logp(
      Level level, String sourceClass, String sourceMethod, String msg, Object params[]) {}

  public void logp(
      Level level, String sourceClass, String sourceMethod, String msg, Throwable thrown) {}

  public void logrb(
      Level level, String sourceClass, String sourceMethod, String bundleName, String msg) {}

  public void logrb(
      Level level,
      String sourceClass,
      String sourceMethod,
      String bundleName,
      String msg,
      Object param1) {}

  public void logrb(
      Level level,
      String sourceClass,
      String sourceMethod,
      String bundleName,
      String msg,
      Object params[]) {}

  public void logrb(
      Level level,
      String sourceClass,
      String sourceMethod,
      ResourceBundle bundle,
      String msg,
      Object... params) {}

  public void logrb(
      Level level,
      String sourceClass,
      String sourceMethod,
      String bundleName,
      String msg,
      Throwable thrown) {}

  public void logrb(
      Level level,
      String sourceClass,
      String sourceMethod,
      ResourceBundle bundle,
      String msg,
      Throwable thrown) {}

  public void throwing(String sourceClass, String sourceMethod, Throwable thrown) {}

  public void setLevel(Level newLevel) throws SecurityException {}
}
