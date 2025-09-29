/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.executors;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.function.ThrowingConsumer;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractExecutorServiceQueueWaitTest<T extends ExecutorService, U extends TestTask> {

  private final T executor;
  private final InstrumentationExtension testing;

  protected AbstractExecutorServiceQueueWaitTest(T executor, InstrumentationExtension testing) {
    this.executor = executor;
    this.testing = testing;
  }

  protected abstract U newTask(Long sleepForMillisSeconds);

  protected T executor() {
    return executor;
  }

  @AfterAll
  void shutdown() throws InterruptedException {
    executor.shutdown();
    executor.awaitTermination(10, TimeUnit.SECONDS);
  }

  @Test
  void executeRunnable() {
    executeAndMeasureQueueWaitForTwoTasks(executor::execute);
  }

  @Test
  void submitRunnable() {
    executeAndMeasureQueueWaitForTwoTasks(task -> executor.submit((Runnable) task));
  }

  @Test
  void submitCallable() {
    executeAndMeasureQueueWaitForTwoTasks(task -> executor.submit((Callable<?>) task));
  }

  @Test
  void invokeAll() {
    executeAndMeasureQueueWaitForTwoTasks(task -> executor.invokeAll(Collections.singleton(task)));
  }

  @Test
  void invokeAllWithTimeout() {
    executeAndMeasureQueueWaitForTwoTasks(task -> executor.invokeAll(Collections.singleton(task), 10, TimeUnit.SECONDS));
  }

  @Test
  void invokeAny() {
    executeAndMeasureQueueWaitForTwoTasks(task -> executor.invokeAny(Collections.singleton(task)));
  }

  @Test
  void invokeAnyWithTimeout() {
    executeAndMeasureQueueWaitForTwoTasks(task -> executor.invokeAny(Collections.singleton(task), 10, TimeUnit.SECONDS));
  }

  // ContextPropagationRunnable is only for lambdas
  @Test
  void executeLambdaRunnable() {
    executeAndMeasureQueueWaitForTwoTasks(task -> executor.execute(() -> task.run()));
  }

  @Test
  void submitLambdaRunnable() {
    executeAndMeasureQueueWaitForTwoTasks(task -> executor.submit(() -> task.run()));
  }

  @Test
  void submitLambdaCallable() {
    executeAndMeasureQueueWaitForTwoTasks(
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

  protected final void executeAndMeasureQueueWaitForTwoTasks(ThrowingConsumer<U> task) {
    testing.runWithSpan(
        "parent",
        () -> {
          // this child will have a span
          U child1 = newTask(1L);
          // this child won't
          U child2 = newTask(1L);
          U child3 = newTask(1L);
          U child4 = newTask(1L);
          U child5 = newTask(1L);
          try {
            task.accept(child1);
            task.accept(child2);
            task.accept(child3);
            task.accept(child4);
            task.accept(child5);
          } catch (Throwable t) {
            throw new AssertionError(t);
          }
          child1.waitForCompletion();
          child2.waitForCompletion();
          child3.waitForCompletion();
          child4.waitForCompletion();
          child5.waitForCompletion();
        });

    testing.waitAndAssertMetrics(
        "io.opentelemetry.executor.queue.wait",
        "executor.queue.wait",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDescription("Time spent waiting in executor queue")
                        .hasUnit("s")
                        .hasHistogramSatisfying(
                            histogram ->
                                histogram.hasPointsSatisfying(
                                    point -> point.hasSumGreaterThan(0.0)
                                )
                        )
            )
    );
//    testing.waitAndAssertTraces(
//        trace ->
//            trace.hasSpansSatisfyingExactly(
//                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
//                span ->
//                    span.hasName("asyncChild")
//                        .hasKind(SpanKind.INTERNAL)
//                        .hasParent(trace.getSpan(0))));
  }

  protected final void executeAndCancelTasks(Function<U, Future<?>> task) {
    List<U> children = new ArrayList<>();
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
//            U child = newTask(false, true);
            U child = newTask(0L);
            children.add(child);
            Future<?> f = task.apply(child);
            jobFutures.add(f);
          }

          jobFutures.forEach(f -> f.cancel(false));
          children.forEach(U::unblock);
        });

    // Just check there is a single trace, this test is primarily to make sure that scopes aren't
    // leaked on cancellation.
    testing.waitAndAssertTraces(trace -> {});
  }
}
