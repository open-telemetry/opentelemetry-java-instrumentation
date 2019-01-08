package datadog.trace.tracer;

import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;

/**
 * Helper class to limit errors logged into application's error log.
 *
 * <p>TODO: can we make this class not public?
 *
 * <p>TODO: once we drop 1.7 support we should be able to use {@code java.time.Clock} instead of
 * {@code System.currentTimeMillis} to simplify testing.
 */
public class LogRateLimiter {

  private final Logger log;
  private final long millisecondsBetweenLog;

  private long nextAllowedLogTime = 0;

  public LogRateLimiter(final Logger log, final long millisecondsBetweenLog) {
    this.log = log;
    this.millisecondsBetweenLog = millisecondsBetweenLog;
  }

  public synchronized void warn(String message, final Object... arguments) {
    if (log.isDebugEnabled()) {
      log.debug(message, arguments);
    } else if (nextAllowedLogTime <= System.currentTimeMillis()) {
      message +=
          " (going silent for "
              + TimeUnit.MILLISECONDS.toMinutes(millisecondsBetweenLog)
              + " minutes)";
      nextAllowedLogTime = System.currentTimeMillis() + millisecondsBetweenLog;
      log.warn(message, arguments);
    }
  }

  public synchronized void error(String message, final Object... arguments) {
    if (log.isDebugEnabled()) {
      log.debug(message, arguments);
    } else if (nextAllowedLogTime <= System.currentTimeMillis()) {
      message +=
          " (going silent for "
              + TimeUnit.MILLISECONDS.toMinutes(millisecondsBetweenLog)
              + " minutes)";
      nextAllowedLogTime = System.currentTimeMillis() + millisecondsBetweenLog;
      log.error(message, arguments);
    }
  }
}
