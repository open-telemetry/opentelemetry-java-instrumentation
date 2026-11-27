/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.resilience4j.circuitbreaker.v2_0;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class DefaultEnablementTest {

  private static final boolean INSTRUMENTATION_ENABLED =
      Boolean.getBoolean("otel.instrumentation.resilience4j-circuitbreaker.enabled");

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void defaultEnablement() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");

    String result = testing.runWithSpan("parent", () -> circuitBreaker.executeSupplier(() -> "ok"));

    assertThat(result).isEqualTo("ok");
    if (INSTRUMENTATION_ENABLED) {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent"),
                  span -> span.hasName("CircuitBreaker test-circuit-breaker")));
    } else {
      testing.waitAndAssertTraces(
          trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("parent")));
    }
  }
}
