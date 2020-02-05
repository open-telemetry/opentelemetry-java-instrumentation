package datadog.common.exec;

import static datadog.common.exec.DaemonThreadFactory.TASK_SCHEDULER;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class SharedExecutors {
  private static final long SHUTDOWN_WAIT_SECONDS = 5;

  private static final ScheduledExecutorService TASK_SCHEDULER_EXECUTOR_SERVICE =
      Executors.newSingleThreadScheduledExecutor(TASK_SCHEDULER);

  static {
    try {
      Runtime.getRuntime().addShutdownHook(new ShutdownCallback(TASK_SCHEDULER_EXECUTOR_SERVICE));
    } catch (final IllegalStateException ex) {
      // The JVM is already shutting down.
    }
  }

  public static ScheduledExecutorService taskScheduler() {
    return TASK_SCHEDULER_EXECUTOR_SERVICE;
  }

  public static ScheduledFuture<?> scheduleTaskAtFixedRate(
      final Runnable command, final long initialDelay, final long period, final TimeUnit unit) {
    return TASK_SCHEDULER_EXECUTOR_SERVICE.scheduleAtFixedRate(command, initialDelay, period, unit);
  }

  public static boolean isTaskSchedulerShutdown() {
    return TASK_SCHEDULER_EXECUTOR_SERVICE.isShutdown();
  }

  private static final class ShutdownCallback extends Thread {

    private final ScheduledExecutorService executorService;

    private ShutdownCallback(final ScheduledExecutorService executorService) {
      super("dd-exec-shutdown-hook");
      this.executorService = executorService;
    }

    @Override
    public void run() {
      executorService.shutdown();
      try {
        if (!executorService.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
          executorService.shutdownNow();
        }
      } catch (final InterruptedException e) {
        executorService.shutdownNow();
      }
    }
  }
}
