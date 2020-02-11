package datadog.common.exec;

import java.util.concurrent.ThreadFactory;

/** A {@link ThreadFactory} implementation that starts all {@link Thread} as daemons. */
public final class DaemonThreadFactory implements ThreadFactory {
  public static final DaemonThreadFactory TRACE_PROCESSOR =
      new DaemonThreadFactory("dd-trace-processor");
  public static final DaemonThreadFactory TRACE_WRITER = new DaemonThreadFactory("dd-trace-writer");
  public static final DaemonThreadFactory TASK_SCHEDULER =
      new DaemonThreadFactory("dd-task-scheduler");

  private final String threadName;

  /**
   * Constructs a new {@code DaemonThreadFactory} with a null ContextClassLoader.
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
    thread.setContextClassLoader(null);
    return thread;
  }
}
