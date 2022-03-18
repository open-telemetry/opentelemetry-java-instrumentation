/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.javaconcurrent;

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
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

abstract class ExecutorInstrumentationTest<T extends ExecutorService>
    extends AbstractExecutorServiceTest<T, JavaAsyncChild> {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  ExecutorInstrumentationTest(T executor) {
    super(executor, testing);
  }

  @Override
  protected JavaAsyncChild newTask(boolean doTraceableWork, boolean blockThread) {
    return new JavaAsyncChild(doTraceableWork, blockThread);
  }

  static class ThreadPoolExecutorTest extends ExecutorInstrumentationTest<ThreadPoolExecutor> {
    ThreadPoolExecutorTest() {
      super(new ThreadPoolExecutor(1, 1, 1000, TimeUnit.NANOSECONDS, new ArrayBlockingQueue<>(20)));
    }
  }

  static class ScheduledThreadPoolExecutorTest
      extends ExecutorInstrumentationTest<ScheduledThreadPoolExecutor> {
    ScheduledThreadPoolExecutorTest() {
      super(new ScheduledThreadPoolExecutor(1));
    }

    @Test
    void scheduleRunnable() {
      executeTwoTasks(task -> executor().schedule((Runnable) task, 10, TimeUnit.MICROSECONDS));
    }

    @Test
    void scheduleCallable() {
      executeTwoTasks(task -> executor().schedule((Callable<?>) task, 10, TimeUnit.MICROSECONDS));
    }

    @Test
    void scheduleLambdaRunnable() {
      executeTwoTasks(task -> executor().schedule(() -> task.run(), 10, TimeUnit.MICROSECONDS));
    }

    @Test
    void scheduleLambdaCallable() {
      executeTwoTasks(
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

  static class ForkJoinPoolTest extends ExecutorInstrumentationTest<ForkJoinPool> {
    ForkJoinPoolTest() {
      super(new ForkJoinPool(20));
    }

    @Test
    void invokeForkJoinTask() {
      executeTwoTasks(task -> executor().invoke((ForkJoinTask<?>) task));
    }

    @Test
    void submitForkJoinTask() {
      executeTwoTasks(task -> executor().submit((ForkJoinTask<?>) task));
    }
  }

  // CustomThreadPoolExecutor would normally be disabled except enabled by system property.
  static class CustomThreadPoolExecutorTest
      extends ExecutorInstrumentationTest<CustomThreadPoolExecutor> {
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
