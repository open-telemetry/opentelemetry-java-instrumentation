/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.javaconcurrent;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class StreamTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void parallelStream() {
    testing.runWithSpan(
        "parent",
        () ->
            IntStream.range(0, 20)
                .parallel()
                .forEach(unused -> testing.runWithSpan("child", () -> {})));

    testing.waitAndAssertTraces(
        trace -> {
          List<Consumer<SpanDataAssert>> assertions = new ArrayList<>();
          assertions.add(span -> span.hasName("parent").hasNoParent());
          IntStream.range(0, 20)
              .forEach(
                  unused ->
                      assertions.add(span -> span.hasName("child").hasParent(trace.getSpan(0))));

          trace.hasSpansSatisfyingExactly(assertions);
        });
  }
}
