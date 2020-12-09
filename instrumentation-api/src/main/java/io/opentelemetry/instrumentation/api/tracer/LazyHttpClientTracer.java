/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import static io.opentelemetry.api.trace.Span.Kind.CLIENT;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;

public abstract class LazyHttpClientTracer<REQUEST, RESPONSE>
    extends HttpClientTracer<REQUEST, RESPONSE> {

  public final LazyHttpClientOperation<REQUEST, RESPONSE> startOperation() {
    return startOperation(DEFAULT_SPAN_NAME);
  }

  public final LazyHttpClientOperation<REQUEST, RESPONSE> startOperation(String name) {
    Context parentContext = Context.current();
    if (inClientSpan(parentContext)) {
      return LazyHttpClientOperation.noop();
    }
    Span clientSpan =
        tracer.spanBuilder(name).setSpanKind(CLIENT).setParent(parentContext).startSpan();
    Context context = withClientSpan(parentContext, clientSpan);
    return LazyHttpClientOperation.create(context, parentContext, this);
  }

  @Override
  protected final void onRequest(SpanBuilder spanBuilder, REQUEST request) {
    // LazyHttpClientTracer does not have request available at start
    throw new IllegalStateException();
  }

  protected void onRequest(Span span, REQUEST request) {
    super.onRequest(span::setAttribute, request);
  }
}
