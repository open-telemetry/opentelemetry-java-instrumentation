package datadog.common.exec;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Delayed;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class CommonTaskExecutor extends AbstractExecutorService {
  public static final CommonTaskExecutor INSTANCE = new CommonTaskExecutor();
  private static final long SHUTDOWN_WAIT_SECONDS = 5;

  private final ScheduledExecutorService executorService =
      Executors.newSingleThreadScheduledExecutor(DaemonThreadFactory.TASK_SCHEDULER);

  private CommonTaskExecutor() {
    try {
      Runtime.getRuntime().addShutdownHook(new ShutdownCallback(executorService));
    } catch (final IllegalStateException ex) {
      // The JVM is already shutting down.
      log.debug("Error adding shutdown hook", ex);
    }
  }

  /**
   * Run {@code task} periodically providing it with {@code target}
   *
   * <p>Important implementation detail here is that internally we do not hold any strong references
   * to {@code target} which means it can be GCed even while periodic task is still scheduled.
   *
   * <p>If {@code target} is GCed periodic task is canceled.
   *
   * @param task task to run. Important: must not hold any strong references to target (or anything
   *     else non static)
   * @param target target object to pass to task
   * @param initialDelay initialDelay, see {@link
   *     ScheduledExecutorService#scheduleAtFixedRate(Runnable, long, long, TimeUnit)}
   * @param period period, see {@link ScheduledExecutorService#scheduleAtFixedRate(Runnable, long,
   *     long, TimeUnit)}
   * @param unit unit, see {@link ScheduledExecutorService#scheduleAtFixedRate(Runnable, long, long,
   *     TimeUnit)}
   * @param name name to use in logs when task cannot be scheduled
   * @return future that can be canceled
   */
  public <T> ScheduledFuture<?> scheduleAtFixedRate(
      final Task<T> task,
      final T target,
      final long initialDelay,
      final long period,
      final TimeUnit unit,
      final String name) {
    if (CommonTaskExecutor.INSTANCE.isShutdown()) {
      log.warn("Periodic task scheduler is shutdown. Will not run: {}", name);
    } else {
      try {
        final PeriodicTask<T> periodicTask = new PeriodicTask<>(task, target);
        final ScheduledFuture<?> future =
            executorService.scheduleAtFixedRate(
                new PeriodicTask<>(task, target), initialDelay, period, unit);
        periodicTask.setFuture(future);
        return future;
      } catch (final RejectedExecutionException e) {
        log.warn("Cleaning task rejected. Will not run: {}", name);
      }
    }
    return new UnscheduledFuture(name);
  }

  @Override
  public void shutdown() {
    executorService.shutdown();
  }

  @Override
  public List<Runnable> shutdownNow() {
    return executorService.shutdownNow();
  }

  @Override
  public boolean isShutdown() {
    return executorService.isShutdown();
  }

  @Override
  public boolean isTerminated() {
    return executorService.isTerminated();
  }

  @Override
  public boolean awaitTermination(final long timeout, final TimeUnit unit)
      throws InterruptedException {
    return executorService.awaitTermination(timeout, unit);
  }

  @Override
  public void execute(final Runnable command) {
    executorService.execute(command);
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

  public interface Task<T> {
    void run(T target);
  }

  public static class PeriodicTask<T> implements Runnable {
    private final WeakReference<T> target;
    private final Task<T> task;
    private volatile ScheduledFuture<?> future = null;

    private PeriodicTask(final Task<T> task, final T target) {
      this.target = new WeakReference<>(target);
      this.task = task;
    }

    @Override
    public void run() {
      final T t = target.get();
      if (t != null) {
        task.run(t);
      } else if (future != null) {
        future.cancel(false);
      }
    }

    public void setFuture(final ScheduledFuture<?> future) {
      this.future = future;
    }
  }

  // Unscheduled future
  @Slf4j
  public static class UnscheduledFuture implements ScheduledFuture<Object> {
    private final String name;

    public UnscheduledFuture(final String name) {
      this.name = name;
    }

    @Override
    public long getDelay(final TimeUnit unit) {
      return 0;
    }

    @Override
    public int compareTo(final Delayed o) {
      return 0;
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
      log.debug("Cancelling future for: {}", name);
      return false;
    }

    @Override
    public boolean isCancelled() {
      return false;
    }

    @Override
    public boolean isDone() {
      return false;
    }

    @Override
    public Object get() {
      return null;
    }

    @Override
    public Object get(final long timeout, final TimeUnit unit) {
      return null;
    }
  }
}
