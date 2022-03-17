/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.javaconcurrent;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.ArrayList;
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
import java.util.function.Function;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.function.ThrowingConsumer;

@SuppressWarnings("ClassCanBeStatic")
class ExecutorInstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class ThreadPoolExecutorTest extends AbstractExecutorServiceTest<ThreadPoolExecutor> {
    ThreadPoolExecutorTest() {
      super(new ThreadPoolExecutor(1, 1, 1000, TimeUnit.NANOSECONDS, new ArrayBlockingQueue<>(20)));
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class ScheduledThreadPoolExecutorTest
      extends AbstractExecutorServiceTest<ScheduledThreadPoolExecutor> {
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

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class ForkJoinPoolTest extends AbstractExecutorServiceTest<ForkJoinPool> {
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
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class CustomThreadPoolExecutorTest extends AbstractExecutorServiceTest<CustomThreadPoolExecutor> {
    CustomThreadPoolExecutorTest() {
      super(new CustomThreadPoolExecutor());
    }
  }

  abstract static class AbstractExecutorServiceTest<T extends ExecutorService> {
    private final T executor;

    AbstractExecutorServiceTest(T executor) {
      this.executor = executor;
    }

    T executor() {
      return executor;
    }

    @AfterAll
    void shutdown() throws Exception {
      executor.shutdown();
      executor.awaitTermination(10, TimeUnit.SECONDS);
    }

    @Test
    void executeRunnable() {
      executeTwoTasks(executor::execute);
    }

    @Test
    void submitRunnable() {
      executeTwoTasks(task -> executor.submit((Runnable) task));
    }

    @Test
    void submitCallable() {
      executeTwoTasks(task -> executor.submit((Callable<?>) task));
    }

    @Test
    void invokeAll() {
      executeTwoTasks(task -> executor.invokeAll(Collections.singleton(task)));
    }

    @Test
    void invokeAllWithTimeout() {
      executeTwoTasks(
          task -> executor.invokeAll(Collections.singleton(task), 10, TimeUnit.SECONDS));
    }

    @Test
    void invokeAny() {
      executeTwoTasks(task -> executor.invokeAny(Collections.singleton(task)));
    }

    @Test
    void invokeAnyWithTimeout() {
      executeTwoTasks(
          task -> executor.invokeAny(Collections.singleton(task), 10, TimeUnit.SECONDS));
    }

    @Test
    void executeLambdaRunnable() {
      executeTwoTasks(task -> executor.execute(() -> task.run()));
    }

    @Test
    void submitLambdaRunnable() {
      executeTwoTasks(task -> executor.submit(() -> task.run()));
    }

    @Test
    void submitLambdaCallable() {
      executeTwoTasks(
          task ->
              executor.submit(
                  () -> {
                    task.run();
                    return null;
                  }));
    }

    @Test
    void submitRunnableAndCancel() {
      executeAndCancelTasks(task -> executor.submit((Runnable) task));
    }

    @Test
    void submitCallableAndCancel() {
      executeAndCancelTasks(task -> executor.submit((Callable<?>) task));
    }
  }

  static void executeTwoTasks(ThrowingConsumer<JavaAsyncChild> task) {
    testing.runWithSpan(
        "parent",
        () -> {
          // this child will have a span
          JavaAsyncChild child1 = new JavaAsyncChild();
          // this child won't
          JavaAsyncChild child2 = new JavaAsyncChild(false, false);
          try {
            task.accept(child1);
            task.accept(child2);
          } catch (Throwable t) {
            throw new AssertionError(t);
          }
          child1.waitForCompletion();
          child2.waitForCompletion();
        });
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("asyncChild")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  static void executeAndCancelTasks(Function<JavaAsyncChild, Future<?>> task) {
    List<JavaAsyncChild> children = new ArrayList<>();
    List<Future<?>> jobFutures = new ArrayList<>();

    testing.runWithSpan(
        "parent",
        () -> {
          for (int i = 0; i < 20; i++) {
            // Our current instrumentation instrumentation does not behave very well
            // if we try to reuse Callable/Runnable. Namely we would be getting 'orphaned'
            // child traces sometimes since state can contain only one parent span - and
            // we do not really have a good way for attributing work to correct parent span
            // if we reuse Callable/Runnable.
            // Solution for now is to never reuse a Callable/Runnable.
            JavaAsyncChild child = new JavaAsyncChild(true, true);
            children.add(child);
            Future<?> f = task.apply(child);
            jobFutures.add(f);
          }

          jobFutures.forEach(f -> f.cancel(false));
          children.forEach(JavaAsyncChild::unblock);
        });

    // Just check there is a single trace, this test is primarily to make sure that scopes aren't
    // leak on
    // cancellation.
    testing.waitAndAssertTraces(trace -> {});
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
