/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.failsafe.v3_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;

import dev.failsafe.CircuitBreaker;
import dev.failsafe.CircuitBreakerOpenException;
import dev.failsafe.Failsafe;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.MetricAssert;
import java.time.Duration;
import java.util.Objects;
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
        Failsafe.with(instrumentedCircuitBreaker)
            .get(
                () -> {
                  if (temp < 2) {
                    return null;
                  } else {
                    return new Object();
                  }
                });
      } catch (CircuitBreakerOpenException e) {
        assertThat(i).isEqualTo(2);
      }
    }

    // then
    testing.waitAndAssertMetrics(
        "io.opentelemetry.failsafe-3.0",
        metricAssert ->
            assertCircuitBreakerMetric(metricAssert, "failsafe.circuit_breaker.failure.count", 2),
        metricAssert ->
            assertCircuitBreakerMetric(metricAssert, "failsafe.circuit_breaker.success.count", 3),
        metricAssert ->
            assertCircuitBreakerMetric(metricAssert, "failsafe.circuit_breaker.open.count", 1),
        metricAssert ->
            assertCircuitBreakerMetric(metricAssert, "failsafe.circuit_breaker.half_open.count", 1),
        metricAssert ->
            assertCircuitBreakerMetric(metricAssert, "failsafe.circuit_breaker.closed.count", 1));
  }

  private static void assertCircuitBreakerMetric(
      MetricAssert metricAssert, String counterName, long expectedValue) {
    metricAssert
        .hasName(counterName)
        .hasLongSumSatisfying(
            sum ->
                sum.isMonotonic()
                    .hasPointsSatisfying(
                        point ->
                            point
                                .hasValue(expectedValue)
                                .hasAttributesSatisfyingExactly(
                                    equalTo(stringKey("name"), "testing"))));
  }
}
