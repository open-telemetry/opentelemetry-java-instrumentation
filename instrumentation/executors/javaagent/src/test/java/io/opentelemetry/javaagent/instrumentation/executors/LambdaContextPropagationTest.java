/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.executors;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Scope;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

// regression test for #9175
class LambdaContextPropagationTest {

  // must be static! the lambda that uses that must be non-capturing
  private static final AtomicInteger failureCounter = new AtomicInteger();

  @Test
  void shouldCorrectlyPropagateContextToRunnables() {
    ExecutorService executor = Executors.newSingleThreadExecutor();

    Baggage baggage = Baggage.builder().put("test", "test").build();
    try (Scope ignored = baggage.makeCurrent()) {
      for (int i = 0; i < 20; i++) {
        // must text execute() -- other methods like submit() decorate the Runnable with a
        // FutureTask
        executor.execute(LambdaContextPropagationTest::assertBaggage);
      }
    }

    assertThat(failureCounter).hasValue(0);
  }

  private static void assertBaggage() {
    if (Baggage.current().getEntryValue("test") == null) {
      failureCounter.incrementAndGet();
    }
  }
}
