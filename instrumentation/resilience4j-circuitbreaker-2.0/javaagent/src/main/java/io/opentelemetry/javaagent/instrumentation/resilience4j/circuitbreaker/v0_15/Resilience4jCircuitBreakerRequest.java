/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.resilience4j.circuitbreaker.v2_0;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.Locale;

class Resilience4jCircuitBreakerRequest {

  private final String name;
  private final String state;

  static Resilience4jCircuitBreakerRequest create(CircuitBreaker circuitBreaker) {
    return new Resilience4jCircuitBreakerRequest(
        circuitBreaker.getName(), circuitBreaker.getState().name().toLowerCase(Locale.ROOT));
  }

  private Resilience4jCircuitBreakerRequest(String name, String state) {
    this.name = name;
    this.state = state;
  }

  String spanName() {
    return "CircuitBreaker " + name;
  }

  String name() {
    return name;
  }

  String state() {
    return state;
  }
}
