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
import io.opentelemetry.api.metrics.Meter;

final class CircuitBreakerEventListenerBuilders {
  private CircuitBreakerEventListenerBuilders() {
    throw new AssertionError();
  }

  static <R> EventListener<ExecutionCompletedEvent<R>> buildInstrumentedFailureListener(
      CircuitBreakerConfig<R> userConfig, Meter meter, Attributes attributes) {
    LongCounter failureCounter =
        meter
            .counterBuilder("failsafe.circuitbreaker.failure.count")
            .setDescription("Count of failed circuit breaker executions")
            .build();
    EventListener<ExecutionCompletedEvent<R>> failureListener = userConfig.getFailureListener();
    return e -> {
      failureCounter.add(1, attributes);
      if (failureListener != null) {
        failureListener.accept(e);
      }
    };
  }

  static <R> EventListener<ExecutionCompletedEvent<R>> buildInstrumentedSuccessListener(
      CircuitBreakerConfig<R> userConfig, Meter meter, Attributes attributes) {
    LongCounter successCounter =
        meter
            .counterBuilder("failsafe.circuitbreaker.success.count")
            .setDescription("Count of successful circuit breaker executions")
            .build();
    EventListener<ExecutionCompletedEvent<R>> successListener = userConfig.getSuccessListener();
    return e -> {
      successCounter.add(1, attributes);
      if (successListener != null) {
        successListener.accept(e);
      }
    };
  }

  static <R> EventListener<CircuitBreakerStateChangedEvent> buildInstrumentedOpenListener(
      CircuitBreakerConfig<R> userConfig, Meter meter, Attributes attributes) {
    LongCounter openCircuitBreakerCounter =
        meter
            .counterBuilder("failsafe.circuitbreaker.open.count")
            .setDescription("Count of times that circuit breaker was opened")
            .build();
    EventListener<CircuitBreakerStateChangedEvent> openListener = userConfig.getOpenListener();
    return e -> {
      openCircuitBreakerCounter.add(1, attributes);
      openListener.accept(e);
    };
  }

  static <R> EventListener<CircuitBreakerStateChangedEvent> buildInstrumentedHalfOpenListener(
      CircuitBreakerConfig<R> userConfig, Meter meter, Attributes attributes) {
    LongCounter halfOpenCircuitBreakerCounter =
        meter
            .counterBuilder("failsafe.circuitbreaker.halfopen.count")
            .setDescription("Count of times that circuit breaker was half-opened")
            .build();
    EventListener<CircuitBreakerStateChangedEvent> halfOpenListener =
        userConfig.getHalfOpenListener();
    return e -> {
      halfOpenCircuitBreakerCounter.add(1, attributes);
      halfOpenListener.accept(e);
    };
  }

  static <R> EventListener<CircuitBreakerStateChangedEvent> buildInstrumentedCloseListener(
      CircuitBreakerConfig<R> userConfig, Meter meter, Attributes attributes) {
    LongCounter closedCircuitBreakerCounter =
        meter
            .counterBuilder("failsafe.circuitbreaker.closed.count")
            .setDescription("Count of times that circuit breaker was closed")
            .build();
    EventListener<CircuitBreakerStateChangedEvent> closeListener = userConfig.getCloseListener();
    return e -> {
      closedCircuitBreakerCounter.add(1, attributes);
      closeListener.accept(e);
    };
  }
}
