/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;

final class PropagatorBasedSpanLinksExtractor<REQUEST> implements SpanLinksExtractor<REQUEST> {
  private final ContextPropagators propagators;
  private final TextMapGetter<REQUEST> getter;

  PropagatorBasedSpanLinksExtractor(ContextPropagators propagators, TextMapGetter<REQUEST> getter) {
    this.propagators = propagators;
    this.getter = getter;
  }

  @Override
  public void extract(SpanLinksBuilder spanLinks, Context parentContext, REQUEST request) {
    Context extracted = propagators.getTextMapPropagator().extract(parentContext, request, getter);
    spanLinks.addLink(Span.fromContext(extracted).getSpanContext());
  }
}
