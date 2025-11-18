/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.function.ThrowingConsumer;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

class SimpleAsyncTaskExecutorInstrumentationTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final SimpleAsyncTaskExecutor EXECUTOR = new SimpleAsyncTaskExecutor();

  private static final Method submitListenableRunnable;
  private static final Method submitListenableCallable;

  static {
    // removed in spring 7
    submitListenableRunnable = findMethod("submitListenable", Runnable.class);
    submitListenableCallable = findMethod("submitListenable", Callable.class);
  }

  private static Method findMethod(String name, Class<?>... parameterTypes) {
    try {
      return SimpleAsyncTaskExecutor.class.getMethod(name, parameterTypes);
    } catch (Exception e) {
      return null;
    }
  }

  @Test
  void executeRunnable() {
    executeTwoTasks(EXECUTOR::execute);
  }

  @Test
  void submitRunnable() {
    executeTwoTasks(task -> EXECUTOR.submit((Runnable) task));
  }

  @Test
  void submitCallable() {
    executeTwoTasks(task -> EXECUTOR.submit((Callable<?>) task));
  }

  @Test
  void submitListenableRunnable() {
    assumeTrue(submitListenableRunnable != null);
    executeTwoTasks(task -> submitListenableRunnable.invoke(EXECUTOR, task));
  }

  @Test
  void submitListenableCallable() {
    assumeTrue(submitListenableCallable != null);
    executeTwoTasks(task -> submitListenableCallable.invoke(EXECUTOR, task));
  }

  private static void executeTwoTasks(ThrowingConsumer<AsyncTask> task) {
    testing.runWithSpan(
        "parent",
        () -> {
          AsyncTask child1 = new AsyncTask(true);
          AsyncTask child2 = new AsyncTask(false);
          try {
            task.accept(child1);
            task.accept(child2);
          } catch (Throwable throwable) {
            throw new AssertionError(throwable);
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

  static class AsyncTask implements Runnable, Callable<Object> {

    private static final Tracer TRACER = GlobalOpenTelemetry.getTracer("test");

    private final boolean startSpan;
    private final CountDownLatch latch = new CountDownLatch(1);

    public AsyncTask(boolean startSpan) {
      this.startSpan = startSpan;
    }

    @Override
    public void run() {
      if (startSpan) {
        TRACER.spanBuilder("asyncChild").startSpan().end();
      }
      latch.countDown();
    }

    @Override
    public Object call() {
      run();
      return null;
    }

    void waitForCompletion() {
      try {
        latch.await();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError(e);
      }
    }
  }
}
