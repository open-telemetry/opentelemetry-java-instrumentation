/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.internal.ContextPropagationDebug;
import java.util.List;

final class ServerInstrumenter<REQUEST, RESPONSE> extends Instrumenter<REQUEST, RESPONSE> {

  private final ContextPropagators propagators;
  private final TextMapGetter<REQUEST> getter;

  ServerInstrumenter(
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
      TextMapGetter<REQUEST> getter) {
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
    this.getter = getter;
  }

  @Override
  public Context start(Context parentContext, REQUEST request) {
    ContextPropagationDebug.debugContextLeakIfEnabled();

    Context extracted = propagators.getTextMapPropagator().extract(parentContext, request, getter);
    return super.start(extracted, request);
  }
}
