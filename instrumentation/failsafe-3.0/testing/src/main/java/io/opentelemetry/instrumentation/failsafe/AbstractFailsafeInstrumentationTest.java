/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.failsafe;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;

import dev.failsafe.CircuitBreaker;
import dev.failsafe.CircuitBreakerOpenException;
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.LongPointAssert;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

public abstract class AbstractFailsafeInstrumentationTest {
  protected abstract InstrumentationExtension testing();

  protected abstract CircuitBreaker<Object> configure(CircuitBreaker<Object> userCircuitBreaker);

  protected abstract RetryPolicy<Object> configure(RetryPolicy<Object> userRetryPolicy);

  @Test
  public void captureCircuitBreakerMetrics() {
    // given
    CircuitBreaker<Object> userCircuitBreaker =
        CircuitBreaker.builder()
            .handleResultIf(Objects::isNull)
            .withFailureThreshold(2)
            .withDelay(Duration.ZERO)
            .withSuccessThreshold(2)
            .build();
    CircuitBreaker<Object> instrumentedCircuitBreaker = configure(userCircuitBreaker);

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
    testing()
        .waitAndAssertMetrics(
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
                                        3, "failsafe.circuit_breaker.outcome", "success"))),
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

  protected void captureRetryPolicyMetrics(@Nullable String expectedPolicyName) {
    RetryPolicy<Object> userRetryPolicy =
        dev.failsafe.RetryPolicy.builder()
            .handleResultIf(Objects::isNull)
            .withMaxAttempts(3)
            .build();
    RetryPolicy<Object> instrumentedRetryPolicy = configure(userRetryPolicy);
    captureRetryPolicyMetrics(
        instrumentedRetryPolicy,
        expectedPolicyName != null ? expectedPolicyName : instrumentedRetryPolicy.toString());
  }

  private void captureRetryPolicyMetrics(
      RetryPolicy<Object> instrumentedRetryPolicy, String expectedPolicyName) {
    // given

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
    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.failsafe-3.0",
            metricAssert ->
                metricAssert
                    .hasName("failsafe.retry_policy.execution.count")
                    .hasLongSumSatisfying(
                        sum ->
                            sum.isMonotonic()
                                .hasPointsSatisfying(
                                    buildRetryPolicyAssertion(2, expectedPolicyName, "failure"),
                                    buildRetryPolicyAssertion(3, expectedPolicyName, "success"))),
            metricAssert ->
                metricAssert
                    .hasName("failsafe.retry_policy.attempts")
                    .hasHistogramSatisfying(
                        histogramAssert ->
                            histogramAssert.hasPointsSatisfying(
                                histogramPointAssert ->
                                    histogramPointAssert
                                        .hasCount(3)
                                        .hasMin(1)
                                        .hasMax(3)
                                        .hasAttributes(
                                            buildExpectedRetryPolicyAttributes(
                                                expectedPolicyName, "success"))
                                        .hasBucketCounts(1L, 1L, 1L, 0L, 0L),
                                histogramPointAssert ->
                                    histogramPointAssert
                                        .hasCount(2)
                                        .hasMin(3)
                                        .hasMax(3)
                                        .hasAttributes(
                                            buildExpectedRetryPolicyAttributes(
                                                expectedPolicyName, "failure"))
                                        .hasBucketCounts(0L, 0L, 2L, 0L, 0L))));
  }

  protected static Consumer<LongPointAssert> buildRetryPolicyAssertion(
      long expectedValue, String expectedPolicyName, String expectedOutcomeValue) {
    return longSumAssert ->
        longSumAssert
            .hasValue(expectedValue)
            .hasAttributes(
                buildExpectedRetryPolicyAttributes(expectedPolicyName, expectedOutcomeValue));
  }

  private static Consumer<LongPointAssert> buildCircuitBreakerAssertion(
      long expectedValue, String expectedAttributeKey, String expectedAttributeValue) {
    return longSumAssert ->
        longSumAssert
            .hasValue(expectedValue)
            .hasAttributesSatisfyingExactly(
                equalTo(stringKey("failsafe.circuit_breaker.name"), "testing"),
                equalTo(stringKey(expectedAttributeKey), expectedAttributeValue));
  }

  private static Attributes buildExpectedRetryPolicyAttributes(
      String expectedPolicyName, String expectedOutcome) {
    return Attributes.builder()
        .put("failsafe.retry_policy.name", expectedPolicyName)
        .put("failsafe.retry_policy.outcome", expectedOutcome)
        .build();
  }
}
