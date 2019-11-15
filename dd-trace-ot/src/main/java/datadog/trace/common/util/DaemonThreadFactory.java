package datadog.trace.common.util;

import java.util.concurrent.ThreadFactory;

/** A {@link ThreadFactory} implementation that starts all {@link Thread} as daemons. */
public final class DaemonThreadFactory implements ThreadFactory {
  private final String threadName;

  /**
   * Constructs a new {@code DaemonThreadFactory}.
   *
   * @param threadName used to prefix all thread names.
   */
  public DaemonThreadFactory(final String threadName) {
    this.threadName = threadName;
  }

  @Override
  public Thread newThread(final Runnable r) {
    final Thread thread = new Thread(r, threadName);
    thread.setDaemon(true);
    return thread;
  }
}
