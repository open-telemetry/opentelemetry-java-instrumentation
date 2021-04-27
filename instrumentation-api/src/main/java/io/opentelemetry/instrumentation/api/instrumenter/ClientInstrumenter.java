/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.util.List;

final class ClientInstrumenter<REQUEST, RESPONSE> extends Instrumenter<REQUEST, RESPONSE> {

  private final ContextPropagators propagators;
  private final TextMapSetter<REQUEST> setter;

  ClientInstrumenter(
      String instrumentationName,
      Tracer tracer,
      Meter meter,
      SpanNameExtractor<? super REQUEST> spanNameExtractor,
      SpanKindExtractor<? super REQUEST> spanKindExtractor,
      SpanStatusExtractor<? super REQUEST, ? super RESPONSE> spanStatusExtractor,
      List<? extends AttributesExtractor<? super REQUEST, ? super RESPONSE>> attributesExtractors,
      List<? extends RequestMetricsFactory> requestMetricsFactories,
      ErrorCauseExtractor errorCauseExtractor,
      ContextPropagators propagators,
      TextMapSetter<REQUEST> setter) {
    super(
        instrumentationName,
        tracer,
        meter,
        spanNameExtractor,
        spanKindExtractor,
        spanStatusExtractor,
        attributesExtractors,
        requestMetricsFactories,
        errorCauseExtractor);
    this.propagators = propagators;
    this.setter = setter;
  }

  @Override
  public Context start(Context parentContext, REQUEST request) {
    Context newContext = super.start(parentContext, request);
    propagators.getTextMapPropagator().inject(newContext, request, setter);
    return newContext;
  }
}
