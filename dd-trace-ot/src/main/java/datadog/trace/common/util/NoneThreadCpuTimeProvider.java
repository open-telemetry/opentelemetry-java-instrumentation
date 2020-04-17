package datadog.trace.common.util;

final class NoneThreadCpuTimeProvider implements ThreadCpuTimeProvider {
  @Override
  public long getThreadCpuTime() {
    return Long.MIN_VALUE;
  }
}
