/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.resilience4j.circuitbreaker.v0_15;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class Resilience4jCircuitBreakerTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void addsCircuitBreakerAttributesOnAcquirePermission() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");

    testing.runWithSpan("parent", circuitBreaker::acquirePermission);

    assertCircuitBreakerSpan("CLOSED");
  }

  @Test
  void addsCircuitBreakerAttributesOnTryAcquirePermission() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");

    boolean permitted = testing.runWithSpan("parent", circuitBreaker::tryAcquirePermission);

    assertThat(permitted).isTrue();
    assertCircuitBreakerSpan("CLOSED");
  }

  @Test
  void addsCircuitBreakerAttributesWhenAcquirePermissionRejected() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    circuitBreaker.transitionToOpenState();

    Throwable thrown =
        catchThrowable(() -> testing.runWithSpan("parent", circuitBreaker::acquirePermission));

    assertThat(thrown).isInstanceOf(CallNotPermittedException.class);
    assertCircuitBreakerSpan("OPEN");
  }

  @Test
  void addsCircuitBreakerAttributesWhenTryAcquirePermissionRejected() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");
    circuitBreaker.transitionToOpenState();

    boolean permitted = testing.runWithSpan("parent", circuitBreaker::tryAcquirePermission);

    assertThat(permitted).isFalse();
    assertCircuitBreakerSpan("OPEN");
  }

  @Test
  void doesNotCreateSpanWithoutActiveSpan() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test-circuit-breaker");

    assertThat(circuitBreaker.tryAcquirePermission()).isTrue();

    assertThat(testing.waitForTraces(0)).isEmpty();
  }

  private static void assertCircuitBreakerSpan(String state) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("parent")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("resilience4j.circuit_breaker.name"),
                                "test-circuit-breaker"),
                            equalTo(stringKey("resilience4j.circuit_breaker.state"), state))));
  }
}
