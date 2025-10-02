/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.failsafe.v3_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.failsafe.CircuitBreaker;
import dev.failsafe.CircuitBreakerOpenException;
import dev.failsafe.Failsafe;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.LongPointAssert;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

final class FailsafeTelemetryTest {
  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void captureCircuitBreakerMetrics() {
    // given
    CircuitBreaker<Object> userCircuitBreaker =
        dev.failsafe.CircuitBreaker.builder()
            .handleResultIf(Objects::isNull)
            .withFailureThreshold(2)
            .withDelay(Duration.ZERO)
            .withSuccessThreshold(2)
            .build();
    FailsafeTelemetry failsafeTelemetry = FailsafeTelemetry.create(testing.getOpenTelemetry());
    CircuitBreaker<Object> instrumentedCircuitBreaker =
        failsafeTelemetry.createCircuitBreaker(userCircuitBreaker, "testing");

    // when
    for (int i = 0; i < 5; i++) {
      try {
        int temp = i;
        Failsafe.with(instrumentedCircuitBreaker).get(() -> temp < 2 ? null : new Object());
      } catch (CircuitBreakerOpenException e) {
        assertThat(i).isEqualTo(2);
      }
    }

    // then
    testing.waitAndAssertMetrics(
        "io.opentelemetry.failsafe-3.0",
        metricAssert ->
            metricAssert
                .hasName("failsafe.circuit_breaker.execution.count")
                .hasLongSumSatisfying(
                    sum ->
                        sum.isMonotonic()
                            .hasPointsSatisfying(
                                buildCircuitBreakerAssertion(
                                    2, "failsafe.circuit_breaker.outcome", "failure"),
                                buildCircuitBreakerAssertion(
                                    3, "failsafe.circuit_breaker.outcome", "success"))));
    testing.waitAndAssertMetrics(
        "io.opentelemetry.failsafe-3.0",
        metricAssert ->
            metricAssert
                .hasName("failsafe.circuit_breaker.state_change.count")
                .hasLongSumSatisfying(
                    sum ->
                        sum.isMonotonic()
                            .hasPointsSatisfying(
                                buildCircuitBreakerAssertion(
                                    1, "failsafe.circuit_breaker.state", "open"),
                                buildCircuitBreakerAssertion(
                                    1, "failsafe.circuit_breaker.state", "half_open"),
                                buildCircuitBreakerAssertion(
                                    1, "failsafe.circuit_breaker.state", "closed"))));
  }

  private static Consumer<LongPointAssert> buildCircuitBreakerAssertion(
      long expectedValue, String expectedAttributeKey, String expectedAttributeValue) {
    return longSumAssert ->
        longSumAssert
            .hasValue(expectedValue)
            .hasAttributesSatisfying(
                attributes ->
                    assertEquals(
                        Attributes.builder()
                            .put("failsafe.circuit_breaker.name", "testing")
                            .put(expectedAttributeKey, expectedAttributeValue)
                            .build(),
                        attributes));
  }
}
