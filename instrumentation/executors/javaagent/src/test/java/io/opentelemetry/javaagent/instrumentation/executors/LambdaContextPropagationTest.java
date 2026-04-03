/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.executors;

import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Scope;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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

  @ParameterizedTest(name = "{0}")
  @MethodSource("executorInvocations")
  void propagateContext(ExecutorInvocation invocation) throws Exception {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Baggage baggage = Baggage.builder().put("test", "test").build();
      try (Scope ignored = baggage.makeCurrent()) {
        for (int i = 0; i < 20; i++) {
          invocation.invoke(executor);
        }
      }

      executor.shutdown();
      assertThat(executor.awaitTermination(30, SECONDS)).isTrue();
    } finally {
      if (!executor.isTerminated()) {
        // Best-effort cleanup to avoid leaked tasks mutating shared static state in later params.
        executor.shutdownNow();
        executor.awaitTermination(30, SECONDS);
      }
    }

    assertThat(failureCounter).hasValue(0);
  }

  private static Stream<Arguments> executorInvocations() {
    return Stream.of(
        Arguments.of(
            named(
                "execute runnable",
                (ExecutorInvocation)
                    executor -> executor.execute(LambdaContextPropagationTest::assertBaggage))),
        Arguments.of(
            named(
                "submit runnable",
                (ExecutorInvocation)
                    executor -> executor.submit(LambdaContextPropagationTest::assertBaggage))),
        Arguments.of(
            named(
                "submit runnable and result",
                (ExecutorInvocation)
                    executor ->
                        executor.submit(LambdaContextPropagationTest::assertBaggage, null))),
        Arguments.of(
            named(
                "submit callable",
                (ExecutorInvocation) executor -> executor.submit(baggageCallable()))),
        Arguments.of(
            named(
                "invoke all",
                (ExecutorInvocation)
                    executor -> executor.invokeAll(Collections.nCopies(20, baggageCallable())))),
        Arguments.of(
            named(
                "invoke any",
                (ExecutorInvocation)
                    executor -> executor.invokeAny(singletonList(baggageCallable())))));
  }

  private static Callable<Void> baggageCallable() {
    return () -> {
      assertBaggage();
      return null;
    };
  }

  @FunctionalInterface
  private interface ExecutorInvocation {
    void invoke(ExecutorService executor) throws Exception;
  }

  private static void assertBaggage() {
    if (Baggage.current().getEntryValue("test") == null) {
      failureCounter.incrementAndGet();
    }
  }
}
