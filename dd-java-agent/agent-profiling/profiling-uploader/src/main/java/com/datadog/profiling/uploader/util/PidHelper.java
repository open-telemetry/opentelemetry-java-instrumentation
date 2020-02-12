package com.datadog.profiling.uploader.util;

import jnr.posix.POSIX;
import jnr.posix.POSIXFactory;
import lombok.extern.slf4j.Slf4j;

/**
 * Get PID In reasonably cross-platform way
 *
 * <p>FIXME: ideally we would like to be able to send PID with root span as well, but currently this
 * end up causing packaging problems. We should revisit this later.
 */
@Slf4j
public class PidHelper {

  public static final String PID_TAG = "process_id";
  public static final Long PID = getPid();

  private static Long getPid() {
    try {
      final Class<?> processHandler = Class.forName("java.lang.ProcessHandle");
      final Object object = processHandler.getMethod("current").invoke(null);
      return (Long) processHandler.getMethod("pid").invoke(object);
    } catch (final Exception e) {
      log.debug("Cannot get PID through JVM API, trying POSIX instead", e);
    }

    try {
      final POSIX posix = POSIXFactory.getPOSIX();
      return (long) posix.getpid();
    } catch (final Exception e) {
      log.debug("Cannot get PID through POSIX API, giving up", e);
    }

    return null;
  }
}
