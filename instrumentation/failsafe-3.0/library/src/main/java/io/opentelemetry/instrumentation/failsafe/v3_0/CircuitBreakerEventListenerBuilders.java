/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.failsafe.v3_0;

import dev.failsafe.CircuitBreakerConfig;
import dev.failsafe.event.CircuitBreakerStateChangedEvent;
import dev.failsafe.event.EventListener;
import dev.failsafe.event.ExecutionCompletedEvent;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;

final class CircuitBreakerEventListenerBuilders {
  private CircuitBreakerEventListenerBuilders() {}

  static <R> EventListener<ExecutionCompletedEvent<R>> buildInstrumentedFailureListener(
      CircuitBreakerConfig<R> userConfig,
      LongCounter executionCounter,
      Attributes commonAttributes) {
    Attributes attributes =
        commonAttributes.toBuilder().put("failsafe.circuit_breaker.outcome", "failure").build();
    EventListener<ExecutionCompletedEvent<R>> failureListener = userConfig.getFailureListener();
    return e -> {
      executionCounter.add(1, attributes);
      if (failureListener != null) {
        failureListener.accept(e);
      }
    };
  }

  static <R> EventListener<ExecutionCompletedEvent<R>> buildInstrumentedSuccessListener(
      CircuitBreakerConfig<R> userConfig,
      LongCounter executionCounter,
      Attributes commonAttributes) {
    Attributes attributes =
        commonAttributes.toBuilder().put("failsafe.circuit_breaker.outcome", "success").build();
    EventListener<ExecutionCompletedEvent<R>> successListener = userConfig.getSuccessListener();
    return e -> {
      executionCounter.add(1, attributes);
      if (successListener != null) {
        successListener.accept(e);
      }
    };
  }

  static <R> EventListener<CircuitBreakerStateChangedEvent> buildInstrumentedOpenListener(
      CircuitBreakerConfig<R> userConfig,
      LongCounter stateChangesCounter,
      Attributes commonAttributes) {
    Attributes attributes =
        commonAttributes.toBuilder().put("failsafe.circuit_breaker.state", "open").build();
    EventListener<CircuitBreakerStateChangedEvent> openListener = userConfig.getOpenListener();
    return e -> {
      stateChangesCounter.add(1, attributes);
      if (openListener != null) {
        openListener.accept(e);
      }
    };
  }

  static <R> EventListener<CircuitBreakerStateChangedEvent> buildInstrumentedHalfOpenListener(
      CircuitBreakerConfig<R> userConfig,
      LongCounter stateChangesCounter,
      Attributes commonAttributes) {
    Attributes attributes =
        commonAttributes.toBuilder().put("failsafe.circuit_breaker.state", "half_open").build();
    EventListener<CircuitBreakerStateChangedEvent> halfOpenListener =
        userConfig.getHalfOpenListener();
    return e -> {
      stateChangesCounter.add(1, attributes);
      if (halfOpenListener != null) {
        halfOpenListener.accept(e);
      }
    };
  }

  static <R> EventListener<CircuitBreakerStateChangedEvent> buildInstrumentedCloseListener(
      CircuitBreakerConfig<R> userConfig,
      LongCounter stateChangesCounter,
      Attributes commonAttributes) {
    Attributes attributes =
        commonAttributes.toBuilder().put("failsafe.circuit_breaker.state", "closed").build();
    EventListener<CircuitBreakerStateChangedEvent> closedListener = userConfig.getCloseListener();
    return e -> {
      stateChangesCounter.add(1, attributes);
      if (closedListener != null) {
        closedListener.accept(e);
      }
    };
  }
}
