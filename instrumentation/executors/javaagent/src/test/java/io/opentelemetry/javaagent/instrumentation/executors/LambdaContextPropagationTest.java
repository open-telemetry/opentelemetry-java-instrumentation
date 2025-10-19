/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.executors;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Scope;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// regression test for:
// https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/9175
// https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/14805
class LambdaContextPropagationTest {

  // must be static! the lambda that uses that must be non-capturing
  private static final AtomicInteger failureCounter = new AtomicInteger();

  @BeforeEach
  void reset() {
    failureCounter.set(0);
  }

  @Test
  void propagateContextExecuteRunnable() throws InterruptedException {
    ExecutorService executor = Executors.newSingleThreadExecutor();

    Baggage baggage = Baggage.builder().put("test", "test").build();
    try (Scope ignored = baggage.makeCurrent()) {
      for (int i = 0; i < 20; i++) {
        executor.execute(LambdaContextPropagationTest::assertBaggage);
      }
    }

    executor.shutdown();
    executor.awaitTermination(30, TimeUnit.SECONDS);

    assertThat(failureCounter).hasValue(0);
  }

  @Test
  void propagateContextSubmitRunnable() throws InterruptedException {
    ExecutorService executor = Executors.newSingleThreadExecutor();

    Baggage baggage = Baggage.builder().put("test", "test").build();
    try (Scope ignored = baggage.makeCurrent()) {
      for (int i = 0; i < 20; i++) {
        executor.submit(LambdaContextPropagationTest::assertBaggage);
      }
    }

    executor.shutdown();
    executor.awaitTermination(30, TimeUnit.SECONDS);

    assertThat(failureCounter).hasValue(0);
  }

  @Test
  void propagateContextSubmitRunnableAndResult() throws InterruptedException {
    ExecutorService executor = Executors.newSingleThreadExecutor();

    Baggage baggage = Baggage.builder().put("test", "test").build();
    try (Scope ignored = baggage.makeCurrent()) {
      for (int i = 0; i < 20; i++) {
        executor.submit(LambdaContextPropagationTest::assertBaggage, null);
      }
    }

    executor.shutdown();
    executor.awaitTermination(30, TimeUnit.SECONDS);

    assertThat(failureCounter).hasValue(0);
  }

  @Test
  void propagateContextSubmitCallable() throws InterruptedException {
    ExecutorService executor = Executors.newSingleThreadExecutor();

    Baggage baggage = Baggage.builder().put("test", "test").build();
    try (Scope ignored = baggage.makeCurrent()) {
      for (int i = 0; i < 20; i++) {
        Callable<?> callable =
            () -> {
              assertBaggage();
              return null;
            };
        executor.submit(callable);
      }
    }

    executor.shutdown();
    executor.awaitTermination(30, TimeUnit.SECONDS);

    assertThat(failureCounter).hasValue(0);
  }

  @Test
  void propagateContextInvokeAll() throws InterruptedException {
    ExecutorService executor = Executors.newSingleThreadExecutor();

    Baggage baggage = Baggage.builder().put("test", "test").build();
    try (Scope ignored = baggage.makeCurrent()) {
      for (int i = 0; i < 20; i++) {
        Callable<Void> callable =
            () -> {
              assertBaggage();
              return null;
            };
        List<Callable<Void>> callables = new ArrayList<>();
        for (int j = 0; j < 20; j++) {
          callables.add(callable);
        }
        executor.invokeAll(callables);
      }
    }

    executor.shutdown();
    executor.awaitTermination(30, TimeUnit.SECONDS);

    assertThat(failureCounter).hasValue(0);
  }

  @Test
  void propagateContextInvokeAny() throws InterruptedException, ExecutionException {
    ExecutorService executor = Executors.newSingleThreadExecutor();

    Baggage baggage = Baggage.builder().put("test", "test").build();
    try (Scope ignored = baggage.makeCurrent()) {
      for (int i = 0; i < 20; i++) {
        Callable<?> callable =
            () -> {
              assertBaggage();
              return null;
            };
        executor.invokeAny(Collections.singletonList(callable));
      }
    }

    executor.shutdown();
    executor.awaitTermination(30, TimeUnit.SECONDS);

    assertThat(failureCounter).hasValue(0);
  }

  private static void assertBaggage() {
    if (Baggage.current().getEntryValue("test") == null) {
      failureCounter.incrementAndGet();
    }
  }
}
