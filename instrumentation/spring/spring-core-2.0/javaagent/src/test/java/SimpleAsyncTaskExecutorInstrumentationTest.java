/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.function.ThrowingConsumer;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

public class SimpleAsyncTaskExecutorInstrumentationTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final SimpleAsyncTaskExecutor EXECUTOR = new SimpleAsyncTaskExecutor();

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
    executeTwoTasks(task -> EXECUTOR.submitListenable((Runnable) task));
  }

  @Test
  void submitListenableCallable() {
    executeTwoTasks(task -> EXECUTOR.submitListenable((Callable<?>) task));
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
