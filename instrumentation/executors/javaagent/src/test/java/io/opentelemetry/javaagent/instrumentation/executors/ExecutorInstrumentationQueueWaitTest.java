/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.executors;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

// copy-paste from ExecutorInstrumentationTest
abstract class ExecutorInstrumentationQueueWaitTest<T extends ExecutorService>
    extends AbstractExecutorServiceQueueWaitTest<T, JavaAsyncQueueWaitChild> {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  ExecutorInstrumentationQueueWaitTest(T executor) {
    super(executor, testing);
  }

  @Override
  protected JavaAsyncQueueWaitChild newTask(Long sleepForMillisSeconds) {
    return new JavaAsyncQueueWaitChild(sleepForMillisSeconds);
  }

  static class ThreadPoolExecutorTest extends ExecutorInstrumentationQueueWaitTest<ThreadPoolExecutor> {
    ThreadPoolExecutorTest() {
      super(new ThreadPoolExecutor(5, 5, 10_000, TimeUnit.NANOSECONDS, new ArrayBlockingQueue<>(20)));
    }
  }

  static class WorkStealingPoolTest extends ExecutorInstrumentationQueueWaitTest<ExecutorService> {
    public WorkStealingPoolTest() {
      super(Executors.newWorkStealingPool(2));
    }
  }

  static class ScheduledThreadPoolExecutorTest
      extends ExecutorInstrumentationQueueWaitTest<ScheduledThreadPoolExecutor> {
    ScheduledThreadPoolExecutorTest() {
      super(new ScheduledThreadPoolExecutor(1));
    }

    @Test
    void scheduleRunnable() {
      executeAndMeasureQueueWaitForTwoTasks(task -> executor().schedule((Runnable) task, 10, TimeUnit.MICROSECONDS));
    }

    @Test
    void scheduleCallable() {
      executeAndMeasureQueueWaitForTwoTasks(task -> executor().schedule((Callable<?>) task, 10, TimeUnit.MICROSECONDS));
    }

    @Test
    void scheduleLambdaRunnable() {
      executeAndMeasureQueueWaitForTwoTasks(task -> executor().schedule(() -> task.run(), 10, TimeUnit.MICROSECONDS));
    }

    @Test
    void scheduleLambdaCallable() {
      executeAndMeasureQueueWaitForTwoTasks(
          task ->
              executor()
                  .schedule(
                      () -> {
                        task.run();
                        return null;
                      },
                      10,
                      TimeUnit.MICROSECONDS));
    }

    @Test
    void scheduleRunnableAndCancel() {
      executeAndCancelTasks(
          task -> executor().schedule((Runnable) task, 10, TimeUnit.MICROSECONDS));
    }

    @Test
    void scheduleCallableAndCancel() {
      executeAndCancelTasks(
          task -> executor().schedule((Callable<?>) task, 10, TimeUnit.MICROSECONDS));
    }
  }

  static class ForkJoinPoolTest extends ExecutorInstrumentationQueueWaitTest<ForkJoinPool> {
    ForkJoinPoolTest() {
      super(new ForkJoinPool(20));
    }

    @Test
    void invokeForkJoinTask() {
      executeAndMeasureQueueWaitForTwoTasks(task -> executor().invoke((ForkJoinTask<?>) task));
    }

    @Test
    void submitForkJoinTask() {
      executeAndMeasureQueueWaitForTwoTasks(task -> executor().submit((ForkJoinTask<?>) task));
    }
  }

   // // CustomThreadPoolExecutor would normally be disabled except enabled by system property.
  static class CustomThreadPoolExecutorTest
      extends ExecutorInstrumentationQueueWaitTest<CustomThreadPoolExecutor> {
    CustomThreadPoolExecutorTest() {
      super(new CustomThreadPoolExecutor());
    }
  }

  @SuppressWarnings("RedundantOverride")
  private static class CustomThreadPoolExecutor extends AbstractExecutorService {

    private volatile boolean running = true;

    private final BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(20);

    private final Thread workerThread =
        new Thread(
            () -> {
              try {
                while (running) {
                  Runnable runnable = workQueue.take();
                  runnable.run();
                }
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
              } catch (Throwable t) {
                throw new AssertionError(t);
              }
            },
            "ExecutorTestThread");

    private CustomThreadPoolExecutor() {
      workerThread.start();
    }

    @Override
    public void shutdown() {
      running = false;
      workerThread.interrupt();
    }

    @Override
    public List<Runnable> shutdownNow() {
      running = false;
      workerThread.interrupt();
      return Collections.emptyList();
    }

    @Override
    public boolean isShutdown() {
      return !running;
    }

    @Override
    public boolean isTerminated() {
      return workerThread.isAlive();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
      workerThread.join(unit.toMillis(timeout));
      return true;
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
      RunnableFuture<T> future = newTaskFor(task);
      execute(future);
      return future;
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
      RunnableFuture<T> future = newTaskFor(task, result);
      execute(future);
      return future;
    }

    @Override
    public Future<?> submit(Runnable task) {
      RunnableFuture<?> future = newTaskFor(task, null);
      execute(future);
      return future;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
      return Collections.singletonList(submit(tasks.stream().findFirst().get()));
    }

    @Override
    public <T> List<Future<T>> invokeAll(
        Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
      return Collections.singletonList(submit(tasks.stream().findFirst().get()));
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) {
      submit(tasks.stream().findFirst().get());
      return null;
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
      submit(tasks.stream().findFirst().get());
      return null;
    }

    @Override
    public void execute(Runnable command) {
      workQueue.add(command);
    }
  }
}
