/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.failsafe.v3_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import dev.failsafe.RetryPolicyConfig;
import dev.failsafe.event.EventListener;
import dev.failsafe.event.ExecutionCompletedEvent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;

final class RetryPolicyEventListenerBuilders {
  private static final AttributeKey<String> OUTCOME_KEY =
      stringKey("failsafe.retry_policy.outcome");

  private RetryPolicyEventListenerBuilders() {}

  static <R> EventListener<ExecutionCompletedEvent<R>> buildInstrumentedFailureListener(
      RetryPolicyConfig<R> userConfig,
      LongCounter executionCounter,
      LongHistogram attemptsHistogram,
      Attributes commonAttributes) {
    Attributes attributes = commonAttributes.toBuilder().put(OUTCOME_KEY, "failure").build();
    EventListener<ExecutionCompletedEvent<R>> userFailureListener = userConfig.getFailureListener();
    return e -> {
      executionCounter.add(1, attributes);
      attemptsHistogram.record(e.getAttemptCount(), attributes);
      if (userFailureListener != null) {
        userFailureListener.accept(e);
      }
    };
  }

  static <R> EventListener<ExecutionCompletedEvent<R>> buildInstrumentedSuccessListener(
      RetryPolicyConfig<R> userConfig,
      LongCounter executionCounter,
      LongHistogram attemptsHistogram,
      Attributes commonAttributes) {
    Attributes attributes = commonAttributes.toBuilder().put(OUTCOME_KEY, "success").build();
    EventListener<ExecutionCompletedEvent<R>> userSuccessListener = userConfig.getSuccessListener();
    return e -> {
      executionCounter.add(1, attributes);
      attemptsHistogram.record(e.getAttemptCount(), attributes);
      if (userSuccessListener != null) {
        userSuccessListener.accept(e);
      }
    };
  }
}
