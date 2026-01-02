/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.failsafe.v3_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.mockito.Mockito.when;

import dev.failsafe.CircuitBreaker;
import dev.failsafe.RetryPolicy;
import dev.failsafe.RetryPolicyConfig;
import dev.failsafe.event.EventListener;
import dev.failsafe.event.ExecutionCompletedEvent;
import io.opentelemetry.instrumentation.failsafe.AbstractFailsafeInstrumentationTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.LongPointAssert;
import java.util.Objects;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;

final class FailsafeTelemetryTest extends AbstractFailsafeInstrumentationTest {
  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected CircuitBreaker<Object> configure(CircuitBreaker<Object> userCircuitBreaker) {
    FailsafeTelemetry failsafeTelemetry = FailsafeTelemetry.create(testing.getOpenTelemetry());
    return failsafeTelemetry.createCircuitBreaker(userCircuitBreaker, "testing");
  }

  @Override
  protected RetryPolicy<Object> configure(RetryPolicy<Object> userRetryPolicy) {
    FailsafeTelemetry failsafeTelemetry = FailsafeTelemetry.create(testing.getOpenTelemetry());
    return failsafeTelemetry.createRetryPolicy(userRetryPolicy, "testing");
  }

  @Test
  void captureRetryPolicyMetrics() {
    captureRetryPolicyMetrics("testing");
  }

  @Test
  @SuppressWarnings("unchecked")
  void createInstrumentedFailureListener() throws Throwable {
    // given
    FailsafeTelemetry failsafeTelemetry = FailsafeTelemetry.create(testing.getOpenTelemetry());
    RetryPolicyConfig<Object> delegate =
        dev.failsafe.RetryPolicy.builder()
            .handleResultIf(Objects::isNull)
            .withMaxAttempts(3)
            .build()
            .getConfig();
    String retryPolicyName = "testing";

    // when
    EventListener<ExecutionCompletedEvent<Object>> actual =
        failsafeTelemetry.createInstrumentedFailureListener(delegate, retryPolicyName);
    ExecutionCompletedEvent<Object> event = Mockito.mock(ExecutionCompletedEvent.class);
    when(event.getAttemptCount()).thenReturn(1);
    actual.accept(event);

    // then
    testing.waitAndAssertMetrics(
        "io.opentelemetry.failsafe-3.0",
        metricAssert ->
            metricAssert
                .hasName("failsafe.retry_policy.execution.count")
                .hasLongSumSatisfying(
                    sum ->
                        sum.isMonotonic()
                            .hasPointsSatisfying(
                                buildRetryPolicyAssertion(1, retryPolicyName, "failure"))));
  }

  @Test
  @SuppressWarnings("unchecked")
  void createInstrumentedSuccessListener() throws Throwable {
    // given
    FailsafeTelemetry failsafeTelemetry = FailsafeTelemetry.create(testing.getOpenTelemetry());
    RetryPolicyConfig<Object> delegate =
        dev.failsafe.RetryPolicy.builder()
            .handleResultIf(Objects::isNull)
            .withMaxAttempts(3)
            .build()
            .getConfig();
    String retryPolicyName = "testing";

    // when
    EventListener<ExecutionCompletedEvent<Object>> actual =
        failsafeTelemetry.createInstrumentedSuccessListener(delegate, retryPolicyName);
    ExecutionCompletedEvent<Object> event = Mockito.mock(ExecutionCompletedEvent.class);
    when(event.getAttemptCount()).thenReturn(1);
    actual.accept(event);

    // then
    testing.waitAndAssertMetrics(
        "io.opentelemetry.failsafe-3.0",
        metricAssert ->
            metricAssert
                .hasName("failsafe.retry_policy.execution.count")
                .hasLongSumSatisfying(
                    sum ->
                        sum.isMonotonic()
                            .hasPointsSatisfying(
                                buildRetryPolicyAssertion(1, retryPolicyName, "success"))));
  }

  private static Consumer<LongPointAssert> buildRetryPolicyAssertion(
      long expectedValue, String expectedOutcomeValue) {
    return longSumAssert ->
        longSumAssert
            .hasValue(expectedValue)
            .hasAttributesSatisfyingExactly(
                equalTo(stringKey("failsafe.retry_policy.name"), "testing"),
                equalTo(stringKey("failsafe.retry_policy.outcome"), expectedOutcomeValue));
  }
}
