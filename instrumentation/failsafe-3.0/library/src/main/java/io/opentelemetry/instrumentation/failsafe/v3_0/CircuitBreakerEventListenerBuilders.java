/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.failsafe.v3_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import dev.failsafe.CircuitBreakerConfig;
import dev.failsafe.event.CircuitBreakerStateChangedEvent;
import dev.failsafe.event.EventListener;
import dev.failsafe.event.ExecutionCompletedEvent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;

final class CircuitBreakerEventListenerBuilders {
  private static final AttributeKey<String> OUTCOME_KEY =
      stringKey("failsafe.circuit_breaker.outcome");
  private static final AttributeKey<String> STATE_KEY = stringKey("failsafe.circuit_breaker.state");

  private CircuitBreakerEventListenerBuilders() {}

  static <R> EventListener<ExecutionCompletedEvent<R>> buildInstrumentedFailureListener(
      CircuitBreakerConfig<R> userConfig,
      LongCounter executionCounter,
      Attributes commonAttributes) {
    Attributes attributes = commonAttributes.toBuilder().put(OUTCOME_KEY, "failure").build();
    return count(executionCounter, attributes, userConfig.getFailureListener());
  }

  static <R> EventListener<ExecutionCompletedEvent<R>> buildInstrumentedSuccessListener(
      CircuitBreakerConfig<R> userConfig,
      LongCounter executionCounter,
      Attributes commonAttributes) {
    Attributes attributes = commonAttributes.toBuilder().put(OUTCOME_KEY, "success").build();
    return count(executionCounter, attributes, userConfig.getSuccessListener());
  }

  static <R> EventListener<CircuitBreakerStateChangedEvent> buildInstrumentedOpenListener(
      CircuitBreakerConfig<R> userConfig,
      LongCounter stateChangesCounter,
      Attributes commonAttributes) {
    Attributes attributes = commonAttributes.toBuilder().put(STATE_KEY, "open").build();
    return count(stateChangesCounter, attributes, userConfig.getOpenListener());
  }

  static <R> EventListener<CircuitBreakerStateChangedEvent> buildInstrumentedHalfOpenListener(
      CircuitBreakerConfig<R> userConfig,
      LongCounter stateChangesCounter,
      Attributes commonAttributes) {
    Attributes attributes = commonAttributes.toBuilder().put(STATE_KEY, "half_open").build();
    return count(stateChangesCounter, attributes, userConfig.getHalfOpenListener());
  }

  static <R> EventListener<CircuitBreakerStateChangedEvent> buildInstrumentedCloseListener(
      CircuitBreakerConfig<R> userConfig,
      LongCounter stateChangesCounter,
      Attributes commonAttributes) {
    Attributes attributes = commonAttributes.toBuilder().put(STATE_KEY, "closed").build();
    return count(stateChangesCounter, attributes, userConfig.getCloseListener());
  }

  private static <T> EventListener<T> count(
      LongCounter counter, Attributes attributes, EventListener<T> delegate) {
    return e -> {
      counter.add(1, attributes);
      if (delegate != null) {
        delegate.accept(e);
      }
    };
  }
}
