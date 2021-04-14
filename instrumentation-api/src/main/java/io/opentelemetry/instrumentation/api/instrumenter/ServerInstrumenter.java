/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.internal.ContextPropagationDebug;
import java.time.Instant;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

final class ServerInstrumenter<REQUEST, RESPONSE> extends Instrumenter<REQUEST, RESPONSE> {

  private final ContextPropagators propagators;
  private final TextMapGetter<REQUEST> getter;

  ServerInstrumenter(
      String instrumentationName,
      Tracer tracer,
      SpanNameExtractor<? super REQUEST> spanNameExtractor,
      SpanKindExtractor<? super REQUEST> spanKindExtractor,
      SpanStatusExtractor<? super REQUEST, ? super RESPONSE> spanStatusExtractor,
      List<? extends AttributesExtractor<? super REQUEST, ? super RESPONSE>> attributesExtractors,
      ErrorCauseExtractor errorCauseExtractor,
      ContextPropagators propagators,
      TextMapGetter<REQUEST> getter) {
    super(
        instrumentationName,
        tracer,
        spanNameExtractor,
        spanKindExtractor,
        spanStatusExtractor,
        attributesExtractors,
        errorCauseExtractor);
    this.propagators = propagators;
    this.getter = getter;
  }

  @Override
  public Context start(Context parentContext, REQUEST request, @Nullable Instant startTime) {
    ContextPropagationDebug.debugContextLeakIfEnabled();

    Context extracted = propagators.getTextMapPropagator().extract(parentContext, request, getter);
    return super.start(extracted, request, startTime);
  }
}
