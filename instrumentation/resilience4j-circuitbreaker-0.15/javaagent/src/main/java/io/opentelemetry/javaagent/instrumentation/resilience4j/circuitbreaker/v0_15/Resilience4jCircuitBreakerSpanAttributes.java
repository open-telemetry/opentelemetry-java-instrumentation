/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.resilience4j.circuitbreaker.v0_15;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;

public final class Resilience4jCircuitBreakerSpanAttributes {

  private static final AttributeKey<String> CIRCUIT_BREAKER_NAME =
      AttributeKey.stringKey("resilience4j.circuit_breaker.name");
  private static final AttributeKey<String> CIRCUIT_BREAKER_STATE =
      AttributeKey.stringKey("resilience4j.circuit_breaker.state");

  public static void set(CircuitBreaker circuitBreaker) {
    Span current = Span.current();
    current.setAttribute(CIRCUIT_BREAKER_NAME, circuitBreaker.getName());
    current.setAttribute(CIRCUIT_BREAKER_STATE, circuitBreaker.getState().name());
  }

  @SuppressWarnings({"ReturnValueIgnored", "unused"})
  private static void limitSupportedVersions(CircuitBreaker circuitBreaker) {
    // tryAcquirePermission was added in 0.15.0. Using it here ensures that muzzle will disable
    // this instrumentation on earlier versions where this method does not exist.
    circuitBreaker.tryAcquirePermission();
  }

  private Resilience4jCircuitBreakerSpanAttributes() {}
}
