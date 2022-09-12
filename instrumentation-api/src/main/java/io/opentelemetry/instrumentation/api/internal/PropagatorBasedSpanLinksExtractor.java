/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class PropagatorBasedSpanLinksExtractor<REQUEST>
    implements SpanLinksExtractor<REQUEST> {

  private final TextMapPropagator propagator;
  private final TextMapGetter<REQUEST> getter;

  public PropagatorBasedSpanLinksExtractor(
      TextMapPropagator propagator, TextMapGetter<REQUEST> getter) {
    this.propagator = propagator;
    this.getter = getter;
  }

  @Override
  public void extract(SpanLinksBuilder spanLinks, Context parentContext, REQUEST request) {
    Context extracted = propagator.extract(parentContext, request, getter);
    spanLinks.addLink(Span.fromContext(extracted).getSpanContext());
  }
}
