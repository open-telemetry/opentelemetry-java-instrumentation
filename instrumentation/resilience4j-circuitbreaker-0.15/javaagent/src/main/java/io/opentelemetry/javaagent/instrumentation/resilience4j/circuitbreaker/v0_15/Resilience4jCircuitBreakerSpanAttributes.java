/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.resilience4j.circuitbreaker.v0_15;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import java.util.Locale;

public class Resilience4jCircuitBreakerSpanAttributes {

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      DeclarativeConfigUtil.getInstrumentationConfig(
              GlobalOpenTelemetry.get(), "resilience4j_circuitbreaker")
          .getBoolean("experimental_span_attributes/development", false);

  private static final AttributeKey<String> CIRCUIT_BREAKER_NAME =
      AttributeKey.stringKey("resilience.policy.name");

  private static final AttributeKey<String> CIRCUIT_BREAKER_STATE =
      AttributeKey.stringKey("resilience.circuit_breaker.state");

  public static void set(CircuitBreaker circuitBreaker) {
    if (!CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      return;
    }

    Span current = Span.current();
    if (!current.isRecording()) {
      return;
    }

    current.setAttribute(CIRCUIT_BREAKER_NAME, circuitBreaker.getName());
    current.setAttribute(
        CIRCUIT_BREAKER_STATE, circuitBreaker.getState().name().toLowerCase(Locale.ROOT));
  }

  @SuppressWarnings({"ReturnValueIgnored", "unused"})
  private static void limitSupportedVersions(CircuitBreaker circuitBreaker) {
    // Keep a reference to enforce 0.15.0 as the minimum version.
    circuitBreaker.tryAcquirePermission();
  }

  private Resilience4jCircuitBreakerSpanAttributes() {}
}
