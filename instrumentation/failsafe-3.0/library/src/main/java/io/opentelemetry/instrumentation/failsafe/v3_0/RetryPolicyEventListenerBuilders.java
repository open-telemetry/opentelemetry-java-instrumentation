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
    return e -> {
      executionCounter.add(1, attributes);
      attemptsHistogram.record(e.getAttemptCount(), attributes);
      if (userConfig.getFailureListener() != null) {
        userConfig.getFailureListener().accept(e);
      }
    };
  }

  static <R> EventListener<ExecutionCompletedEvent<R>> buildInstrumentedSuccessListener(
      RetryPolicyConfig<R> userConfig,
      LongCounter executionCounter,
      LongHistogram attemptsHistogram,
      Attributes commonAttributes) {
    Attributes attributes = commonAttributes.toBuilder().put(OUTCOME_KEY, "success").build();
    return e -> {
      executionCounter.add(1, attributes);
      attemptsHistogram.record(e.getAttemptCount(), attributes);
      if (userConfig.getFailureListener() != null) {
        userConfig.getFailureListener().accept(e);
      }
    };
  }
}
