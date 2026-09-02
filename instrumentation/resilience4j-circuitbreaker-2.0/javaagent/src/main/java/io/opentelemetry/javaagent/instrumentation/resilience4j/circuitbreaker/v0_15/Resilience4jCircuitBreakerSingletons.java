/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.resilience4j.circuitbreaker.v2_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;

public class Resilience4jCircuitBreakerSingletons {

  private static final String INSTRUMENTATION_NAME =
      "io.opentelemetry.resilience4j-circuitbreaker-2.0";

  private static final Instrumenter<Resilience4jCircuitBreakerRequest, String> instrumenter =
      createInstrumenter();

  public static Instrumenter<Resilience4jCircuitBreakerRequest, String> instrumenter() {
    return instrumenter;
  }

  private static Instrumenter<Resilience4jCircuitBreakerRequest, String> createInstrumenter() {
    InstrumenterBuilder<Resilience4jCircuitBreakerRequest, String> builder =
        Instrumenter.<Resilience4jCircuitBreakerRequest, String>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                Resilience4jCircuitBreakerRequest::spanName)
            .setSpanStatusExtractor(
                (spanStatusBuilder, request, outcome, error) -> {
                  if ("failure".equals(outcome) || "rejected".equals(outcome)) {
                    spanStatusBuilder.setStatus(StatusCode.ERROR);
                  } else {
                    SpanStatusExtractor.getDefault()
                        .extract(spanStatusBuilder, request, outcome, error);
                  }
                });

    if (DeclarativeConfigUtil.getInstrumentationConfig(
            GlobalOpenTelemetry.get(), "resilience4j_circuitbreaker")
        .getBoolean("experimental_span_attributes/development", false)) {
      builder.addAttributesExtractor(new Resilience4jCircuitBreakerAttributesExtractor());
    }

    return builder.buildInstrumenter();
  }

  private Resilience4jCircuitBreakerSingletons() {}
}
