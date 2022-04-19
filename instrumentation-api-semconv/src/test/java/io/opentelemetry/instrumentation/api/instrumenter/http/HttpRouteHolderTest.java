/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import org.junit.jupiter.api.Test;

class HttpRouteHolderTest {

  @Test
  void shouldSetRouteEvenIfSpanIsNotSampled() {
    Span span =
        Span.wrap(
            SpanContext.create(
                "00000000000000000000000000000042",
                "0000000000000012",
                TraceFlags.getDefault(),
                TraceState.getDefault()));

    Context context = Context.root();
    context = context.with(span);
    context = SpanKey.HTTP_SERVER.storeInContext(context, span);
    context = HttpRouteHolder.get().start(context, null, Attributes.empty());

    assertNull(HttpRouteHolder.getRoute(context));

    HttpRouteHolder.updateHttpRoute(context, HttpRouteSource.SERVLET, "/get/:id");

    assertEquals("/get/:id", HttpRouteHolder.getRoute(context));
  }

  // TODO(mateusz): add more unit tests for HttpRouteHolder
}
