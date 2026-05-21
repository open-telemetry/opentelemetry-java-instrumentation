/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.failsafe.v3_0.internal;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.util.Arrays.asList;

import dev.failsafe.RetryPolicyConfig;
import dev.failsafe.event.EventListener;
import dev.failsafe.event.ExecutionCompletedEvent;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class RetryPolicyEventListenerBuilders {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.failsafe-3.0";

  private static final AttributeKey<String> OUTCOME_KEY =
      stringKey("failsafe.retry_policy.outcome");
  private static final AttributeKey<String> RETRY_POLICY_NAME =
      AttributeKey.stringKey("failsafe.retry_policy.name");

  private RetryPolicyEventListenerBuilders() {}

  public static <R> EventListener<ExecutionCompletedEvent<R>> buildInstrumentedFailureListener(
      OpenTelemetry openTelemetry, RetryPolicyConfig<R> userConfig, String policyName) {
    LongCounter executionCounter = buildRetryPolicyExecutionCounter(openTelemetry);
    LongHistogram attemptsHistogram = buildRetryPolicyAttemptsHistogram(openTelemetry);
    Attributes attributes = Attributes.of(RETRY_POLICY_NAME, policyName, OUTCOME_KEY, "failure");
    EventListener<ExecutionCompletedEvent<R>> userFailureListener = userConfig.getFailureListener();
    return e -> {
      executionCounter.add(1, attributes);
      attemptsHistogram.record(e.getAttemptCount(), attributes);
      if (userFailureListener != null) {
        userFailureListener.accept(e);
      }
    };
  }

  public static <R> EventListener<ExecutionCompletedEvent<R>> buildInstrumentedSuccessListener(
      OpenTelemetry openTelemetry, RetryPolicyConfig<R> userConfig, String policyName) {
    LongCounter executionCounter = buildRetryPolicyExecutionCounter(openTelemetry);
    LongHistogram attemptsHistogram = buildRetryPolicyAttemptsHistogram(openTelemetry);
    Attributes attributes = Attributes.of(RETRY_POLICY_NAME, policyName, OUTCOME_KEY, "success");
    EventListener<ExecutionCompletedEvent<R>> userSuccessListener = userConfig.getSuccessListener();
    return e -> {
      executionCounter.add(1, attributes);
      attemptsHistogram.record(e.getAttemptCount(), attributes);
      if (userSuccessListener != null) {
        userSuccessListener.accept(e);
      }
    };
  }

  private static LongCounter buildRetryPolicyExecutionCounter(OpenTelemetry openTelemetry) {
    return openTelemetry
        .getMeter(INSTRUMENTATION_NAME)
        .counterBuilder("failsafe.retry_policy.execution.count")
        .setDescription(
            "Count of execution attempts processed by the retry policy, "
                + "where one execution represents the total number of attempts.")
        .setUnit("{execution}")
        .build();
  }

  private static LongHistogram buildRetryPolicyAttemptsHistogram(OpenTelemetry openTelemetry) {
    return openTelemetry
        .getMeter(INSTRUMENTATION_NAME)
        .histogramBuilder("failsafe.retry_policy.attempts")
        .setDescription("Number of attempts for each execution.")
        .setUnit("{attempt}")
        .ofLongs()
        .setExplicitBucketBoundariesAdvice(asList(1L, 2L, 3L, 5L))
        .build();
  }
}
