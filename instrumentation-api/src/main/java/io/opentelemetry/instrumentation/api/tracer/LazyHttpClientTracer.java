/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import static io.opentelemetry.api.trace.Span.Kind.CLIENT;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;

public abstract class LazyHttpClientTracer<REQUEST, CARRIER, RESPONSE>
    extends HttpClientTracer<REQUEST, CARRIER, RESPONSE> {

  public final LazyHttpClientOperation<REQUEST, CARRIER, RESPONSE> startOperation() {
    return startOperation(DEFAULT_SPAN_NAME);
  }

  public final LazyHttpClientOperation<REQUEST, CARRIER, RESPONSE> startOperation(String name) {
    Context parentContext = Context.current();
    if (!shouldStartSpan(parentContext)) {
      return LazyHttpClientOperation.noop();
    }
    Span clientSpan =
        tracer.spanBuilder(name).setSpanKind(CLIENT).setParent(parentContext).startSpan();
    Context context = withClientSpan(parentContext, clientSpan);
    return LazyHttpClientOperation.create(context, parentContext, this);
  }
}
