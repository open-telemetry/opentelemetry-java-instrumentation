/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.resilience4j.circuitbreaker.v2_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

class Resilience4jCircuitBreakerAttributesExtractor
    implements AttributesExtractor<Resilience4jCircuitBreakerRequest, String> {

  private static final AttributeKey<String> CIRCUIT_BREAKER_NAME =
      stringKey("resilience4j.circuit_breaker.name");

  private static final AttributeKey<String> CIRCUIT_BREAKER_STATE =
      stringKey("resilience4j.circuit_breaker.state");

  private static final AttributeKey<String> CIRCUIT_BREAKER_OUTCOME =
      stringKey("resilience4j.circuit_breaker.outcome");

  @Override
  public void onStart(
      AttributesBuilder attributes,
      Context parentContext,
      Resilience4jCircuitBreakerRequest request) {
    attributes.put(CIRCUIT_BREAKER_NAME, request.name());
    attributes.put(CIRCUIT_BREAKER_STATE, request.state());
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      Resilience4jCircuitBreakerRequest request,
      @Nullable String outcome,
      @Nullable Throwable error) {
    attributes.put(CIRCUIT_BREAKER_OUTCOME, outcome);
  }
}
