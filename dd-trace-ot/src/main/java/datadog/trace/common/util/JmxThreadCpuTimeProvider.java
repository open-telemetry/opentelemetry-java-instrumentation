package datadog.trace.common.util;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

/** Thread CPU time provider based on {@linkplain ThreadMXBean#getCurrentThreadCpuTime()} */
final class JmxThreadCpuTimeProvider implements ThreadCpuTimeProvider {
  private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

  public static final JmxThreadCpuTimeProvider INSTANCE = new JmxThreadCpuTimeProvider();

  private JmxThreadCpuTimeProvider() {}

  /**
   * @return the actual thread CPU time as reported by {@linkplain
   *     ThreadMXBean#getCurrentThreadCpuTime()}
   */
  @Override
  public long getThreadCpuTime() {
    return threadMXBean.getCurrentThreadCpuTime();
  }
}
