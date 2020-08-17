/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.javaagent.tooling;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Delayed;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CommonTaskExecutor extends AbstractExecutorService {

  private static final Logger log = LoggerFactory.getLogger(CommonTaskExecutor.class);

  public static final CommonTaskExecutor INSTANCE = new CommonTaskExecutor();
  private static final long SHUTDOWN_WAIT_SECONDS = 5;

  private final ScheduledExecutorService executorService =
      Executors.newSingleThreadScheduledExecutor(DaemonThreadFactory.TASK_SCHEDULER);

  private CommonTaskExecutor() {
    try {
      Runtime.getRuntime().addShutdownHook(new ShutdownCallback(executorService));
    } catch (IllegalStateException ex) {
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
   * <p>This method should be able to schedule task in majority of cases. The only reasonable case
   * when this would fail is when task is being scheduled during JVM shutdown. In this case this
   * method will return 'fake' future that can still be canceled to avoid confusing callers.
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
      Task<T> task, T target, long initialDelay, long period, TimeUnit unit, String name) {
    if (CommonTaskExecutor.INSTANCE.isShutdown()) {
      log.warn("Periodic task scheduler is shutdown. Will not run: {}", name);
    } else {
      try {
        PeriodicTask<T> periodicTask = new PeriodicTask<>(task, target);
        ScheduledFuture<?> future =
            executorService.scheduleAtFixedRate(periodicTask, initialDelay, period, unit);
        periodicTask.setFuture(future);
        return future;
      } catch (RejectedExecutionException e) {
        log.warn("Periodic task rejected. Will not run: {}", name);
      }
    }
    /*
     * Return a 'fake' unscheduled future to allow caller call 'cancel' on it if needed.
     * We are using 'fake' object instead of null to avoid callers needing to deal with nulls.
     */
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
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return executorService.awaitTermination(timeout, unit);
  }

  @Override
  public void execute(Runnable command) {
    executorService.execute(command);
  }

  private static final class ShutdownCallback extends Thread {

    private final ScheduledExecutorService executorService;

    private ShutdownCallback(ScheduledExecutorService executorService) {
      super("opentelemetry-exec-shutdown-hook");
      this.executorService = executorService;
    }

    @Override
    public void run() {
      executorService.shutdown();
      try {
        if (!executorService.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
          executorService.shutdownNow();
        }
      } catch (InterruptedException e) {
        executorService.shutdownNow();
      }
    }
  }

  public interface Task<T> {
    void run(T target);
  }

  private static class PeriodicTask<T> implements Runnable {
    private final WeakReference<T> target;
    private final Task<T> task;
    private volatile ScheduledFuture<?> future = null;

    public PeriodicTask(Task<T> task, T target) {
      this.target = new WeakReference<>(target);
      this.task = task;
    }

    @Override
    public void run() {
      T t = target.get();
      if (t != null) {
        task.run(t);
      } else if (future != null) {
        future.cancel(false);
      }
    }

    public void setFuture(ScheduledFuture<?> future) {
      this.future = future;
    }
  }

  // Unscheduled future
  private static class UnscheduledFuture implements ScheduledFuture<Object> {

    private static final Logger log = LoggerFactory.getLogger(UnscheduledFuture.class);

    private final String name;

    public UnscheduledFuture(String name) {
      this.name = name;
    }

    @Override
    public long getDelay(TimeUnit unit) {
      return 0;
    }

    @Override
    public int compareTo(Delayed o) {
      return 0;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      log.debug("Cancelling unscheduled future for: {}", name);
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
    public Object get(long timeout, TimeUnit unit) {
      return null;
    }
  }
}
