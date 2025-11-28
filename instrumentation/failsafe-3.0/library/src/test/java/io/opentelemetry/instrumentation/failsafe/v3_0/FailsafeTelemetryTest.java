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
import dev.failsafe.RetryPolicy;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.metrics.data.HistogramData;
import io.opentelemetry.sdk.metrics.data.HistogramPointData;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.SumData;
import io.opentelemetry.sdk.testing.assertj.LongPointAssert;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
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

  @Test
  void captureRetryPolicyMetrics() {
    // given
    RetryPolicy<Object> userRetryPolicy =
        dev.failsafe.RetryPolicy.builder()
            .handleResultIf(Objects::isNull)
            .withMaxAttempts(3)
            .build();
    FailsafeTelemetry failsafeTelemetry = FailsafeTelemetry.create(testing.getOpenTelemetry());
    RetryPolicy<Object> instrumentedRetryPolicy =
        failsafeTelemetry.createRetryPolicy(userRetryPolicy, "testing");

    // when
    for (int i = 0; i <= 4; i++) {
      int temp = i;
      AtomicInteger retry = new AtomicInteger(0);
      Failsafe.with(instrumentedRetryPolicy)
          .get(
              () -> {
                if (retry.get() < temp) {
                  retry.incrementAndGet();
                  return null;
                } else {
                  return new Object();
                }
              });
    }

    // then
    testing.waitAndAssertMetrics("io.opentelemetry.failsafe-3.0");
    assertThat(testing.metrics().size()).isEqualTo(2);

    SumData<LongPointData> executionCountMetric =
        testing.metrics().stream()
            .filter(m -> m.getName().equals("failsafe.retry_policy.execution.count"))
            .findFirst()
            .get()
            .getLongSumData();
    assertThat(executionCountMetric.getPoints().size()).isEqualTo(2);
    assertThat(executionCountMetric.getPoints())
        .anyMatch(
            p ->
                p.getAttributes().equals(buildExpectedRetryPolicyAttributes("failure"))
                    && p.getValue() == 2);
    assertThat(executionCountMetric.getPoints())
        .anyMatch(
            p ->
                p.getAttributes().equals(buildExpectedRetryPolicyAttributes("success"))
                    && p.getValue() == 3);

    HistogramData attemptsMetric =
        testing.metrics().stream()
            .filter(m -> m.getName().equals("failsafe.retry_policy.attempts"))
            .findFirst()
            .get()
            .getHistogramData();
    Collection<HistogramPointData> pointData = attemptsMetric.getPoints();
    assertThat(pointData).hasSize(2);
    assertThat(pointData)
        .anyMatch(
            p ->
                p.getCount() == 3
                    && p.getMin() == 1
                    && p.getMax() == 3
                    && p.getAttributes().equals(buildExpectedRetryPolicyAttributes("success"))
                    && Arrays.equals(p.getCounts().toArray(), new Long[] {1L, 1L, 1L, 0L, 0L}));
    assertThat(pointData)
        .anyMatch(
            p ->
                p.getCount() == 2
                    && p.getMin() == 3
                    && p.getMax() == 3
                    && p.getAttributes().equals(buildExpectedRetryPolicyAttributes("failure"))
                    && Arrays.equals(p.getCounts().toArray(), new Long[] {0L, 0L, 2L, 0L, 0L}));
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

  private static Attributes buildExpectedRetryPolicyAttributes(String expectedOutcome) {
    return Attributes.builder()
        .put("failsafe.retry_policy.name", "testing")
        .put("failsafe.retry_policy.outcome", expectedOutcome)
        .build();
  }
}
