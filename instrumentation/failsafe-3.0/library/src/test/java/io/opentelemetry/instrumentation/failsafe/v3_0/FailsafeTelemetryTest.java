/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.failsafe.v3_0;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.failsafe.CircuitBreakerOpenException;
import dev.failsafe.Failsafe;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.assertj.MetricAssert;
import java.time.Duration;
import java.util.Objects;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

final class FailsafeTelemetryTest {
  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Nested
  final class CircuitBreaker {
    @Test
    void should_Capture_CircuitBreaker_Metrics() {
      // given
      dev.failsafe.CircuitBreaker<Object> userCircuitBreaker =
          dev.failsafe.CircuitBreaker.builder()
              .handleResultIf(Objects::isNull)
              .withFailureThreshold(2)
              .withDelay(Duration.ZERO)
              .withSuccessThreshold(2)
              .build();
      FailsafeTelemetry failsafeTelemetry = FailsafeTelemetry.create(testing.getOpenTelemetry());
      dev.failsafe.CircuitBreaker<Object> instrumentedCircuitBreaker =
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
          assertThat(i, equalTo(2));
        }
      }

      // then
      testing.waitAndAssertMetrics(
          "io.opentelemetry.failsafe-3.0",
          metricAssert ->
              assertCircuitBreakerMetric(metricAssert, "failsafe.circuitbreaker.failure.count", 2),
          metricAssert ->
              assertCircuitBreakerMetric(metricAssert, "failsafe.circuitbreaker.success.count", 3),
          metricAssert ->
              assertCircuitBreakerMetric(metricAssert, "failsafe.circuitbreaker.open.count", 1),
          metricAssert ->
              assertCircuitBreakerMetric(metricAssert, "failsafe.circuitbreaker.halfopen.count", 1),
          metricAssert ->
              assertCircuitBreakerMetric(metricAssert, "failsafe.circuitbreaker.closed.count", 1));
    }
  }

  private static void assertCircuitBreakerMetric(
      MetricAssert metricAssert, String counterName, long expectedValue) {
    MetricData closeCountData = metricAssert.actual();
    assertThat(closeCountData.getName(), equalTo(counterName));
    assertTrue(closeCountData.getData().getPoints().stream().findFirst().isPresent());
    LongPointData closeCountLongPointData =
        (LongPointData) closeCountData.getData().getPoints().stream().findFirst().get();
    assertThat(
        closeCountLongPointData.getAttributes(),
        equalTo(Attributes.of(AttributeKey.stringKey("name"), "testing")));
    assertThat(closeCountLongPointData.getValue(), equalTo(expectedValue));
  }
}
