/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.failsafe.v3_0;

import static io.opentelemetry.instrumentation.failsafe.v3_0.CircuitBreakerEventListenerBuilders.buildInstrumentedCloseListener;
import static io.opentelemetry.instrumentation.failsafe.v3_0.CircuitBreakerEventListenerBuilders.buildInstrumentedFailureListener;
import static io.opentelemetry.instrumentation.failsafe.v3_0.CircuitBreakerEventListenerBuilders.buildInstrumentedHalfOpenListener;
import static io.opentelemetry.instrumentation.failsafe.v3_0.CircuitBreakerEventListenerBuilders.buildInstrumentedOpenListener;
import static io.opentelemetry.instrumentation.failsafe.v3_0.CircuitBreakerEventListenerBuilders.buildInstrumentedSuccessListener;

import dev.failsafe.CircuitBreaker;
import dev.failsafe.CircuitBreakerConfig;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;

/** Entrypoint for instrumenting Failsafe components. */
public final class FailsafeTelemetry {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.failsafe-3.0";

  private static final AttributeKey<String> CIRCUIT_BREAKER_NAME =
      AttributeKey.stringKey("failsafe.circuit_breaker.name");

  /** Returns a new {@link FailsafeTelemetry} configured with the given {@link OpenTelemetry}. */
  public static FailsafeTelemetry create(OpenTelemetry openTelemetry) {
    return new FailsafeTelemetry(openTelemetry);
  }

  private final OpenTelemetry openTelemetry;

  private FailsafeTelemetry(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Returns an instrumented {@link CircuitBreaker} by given values.
   *
   * @param delegate user configured {@link CircuitBreaker} to be instrumented
   * @param circuitBreakerName identifier of given {@link CircuitBreaker}
   * @param <R> {@link CircuitBreaker}'s result type
   * @return instrumented {@link CircuitBreaker}
   */
  public <R> CircuitBreaker<R> createCircuitBreaker(
      CircuitBreaker<R> delegate, String circuitBreakerName) {
    CircuitBreakerConfig<R> userConfig = delegate.getConfig();
    Meter meter = openTelemetry.getMeter(INSTRUMENTATION_NAME);
    LongCounter executionCounter =
        meter
            .counterBuilder("failsafe.circuit_breaker.execution.count")
            .setDescription("Count of circuit breaker executions.")
            .setUnit("{execution}")
            .build();
    LongCounter stateChangesCounter =
        meter
            .counterBuilder("failsafe.circuit_breaker.state_changes.count")
            .setDescription("Count of circuit breaker state changes.")
            .setUnit("{execution}")
            .build();
    Attributes attributes = Attributes.of(CIRCUIT_BREAKER_NAME, circuitBreakerName);
    return CircuitBreaker.builder(userConfig)
        .onFailure(buildInstrumentedFailureListener(userConfig, executionCounter, attributes))
        .onSuccess(buildInstrumentedSuccessListener(userConfig, executionCounter, attributes))
        .onOpen(buildInstrumentedOpenListener(userConfig, stateChangesCounter, attributes))
        .onHalfOpen(buildInstrumentedHalfOpenListener(userConfig, stateChangesCounter, attributes))
        .onClose(buildInstrumentedCloseListener(userConfig, stateChangesCounter, attributes))
        .build();
  }
}
