/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.ClientSpan;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public abstract class Instrumenter<REQUEST, RESPONSE> {

  private final Tracer tracer;
  private final List<AttributesExtractor<? super REQUEST, ? super RESPONSE>> extractors;

  protected Instrumenter(
      Tracer tracer, Iterable<? extends AttributesExtractor<? super REQUEST, ? super RESPONSE>> extractors) {
    this.tracer = tracer;
    this.extractors =
        StreamSupport.stream(extractors.spliterator(), false).collect(Collectors.toList());
  }

  public Context start(Context parentContext, REQUEST request) {
    SpanKind kind = spanKind(request);
    SpanBuilder spanBuilder = tracer.spanBuilder(spanName(request)).setSpanKind(kind);

    AttributesBuilder attributesBuilder = Attributes.builder();
    for (AttributesExtractor<? super REQUEST, ? super RESPONSE> extractor : extractors) {
      extractor.onStart(attributesBuilder, request);
    }

    attributesBuilder
        .build()
        .forEach(
            (key, value) -> {
              spanBuilder.setAttribute((AttributeKey) key, value);
            });

    Span span = spanBuilder.startSpan();
    Context context = parentContext.with(span);
    switch (kind) {
      case SERVER:
        return ServerSpan.with(context, span);
      case CLIENT:
        return ClientSpan.with(context, span);
      default:
        return context;
    }
  }

  public void end(Context context, REQUEST request, RESPONSE response, Throwable error) {
    Span span = Span.fromContext(context);

    AttributesBuilder attributesBuilder = Attributes.builder();
    for (AttributesExtractor<? super REQUEST, ? super RESPONSE> extractor : extractors) {
      extractor.onEnd(attributesBuilder, request, response);
    }

    attributesBuilder
        .build()
        .forEach(
            (key, value) -> {
              span.setAttribute((AttributeKey) key, value);
            });

    if (error != null) {
      span.recordException(error);
    }

    span.end();
  }

  protected abstract String spanName(REQUEST request);

  protected abstract SpanKind spanKind(REQUEST request);
}
