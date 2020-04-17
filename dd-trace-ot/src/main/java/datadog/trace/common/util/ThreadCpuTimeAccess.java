package datadog.trace.common.util;

import datadog.trace.api.Config;
import lombok.extern.slf4j.Slf4j;

/**
 * Thread CPU time accessor.<br>
 * This class abstracts away the actual method used to get the current thread CPU time.
 */
@Slf4j
public final class ThreadCpuTimeAccess {
  private static volatile ThreadCpuTimeProvider cpuTimeProvider = ThreadCpuTimeProvider.NONE;

  /**
   * Disable JMX based thread CPU time. Will flip back to the {@linkplain
   * ThreadCpuTimeProvider#NONE} implementation.
   */
  public static void disableJmx() {
    log.debug("Disabling JMX thread CPU time provider");
    cpuTimeProvider = ThreadCpuTimeProvider.NONE;
  }

  /** Enable JMX based thread CPU time */
  public static void enableJmx() {
    if (!Config.get().isProfilingEnabled()) {
      log.debug("Will not enable thread CPU time access. Profiling is disabled.");
      return;
    }
    try {
      log.debug("Enabling JMX thread CPU time provider");
      /*
       * Can not use direct class reference to JmxThreadCpuTimeProvider since on some rare JVM implementations
       * using eager class resolution that class could be resolved at the moment when ThreadCpuTime is being loaded,
       * potentially triggering j.u.l initialization which is potentially dangerous and can be done only at certain
       * point in time.
       * Using reflection should alleviate this problem - no class constant to resolve during class load. The JMX
       * thread cpu time provider will be loaded at exact moment when the reflection code is executed. Then it is up
       * to the caller to ensure that it is safe to use JMX.
       */
      cpuTimeProvider =
          (ThreadCpuTimeProvider)
              Class.forName("datadog.trace.common.util.JmxThreadCpuTimeProvider")
                  .getField("INSTANCE")
                  .get(null);
    } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
      log.info("Unable to initialize JMX thread CPU time provider", e);
    }
  }

  /**
   * Get the current thread CPU time
   *
   * @return the actual current thread CPU time or {@linkplain Long#MIN_VALUE} if the JMX provider
   *     is not available
   */
  public static long getCurrentThreadCpuTime() {
    return cpuTimeProvider.getThreadCpuTime();
  }
}
