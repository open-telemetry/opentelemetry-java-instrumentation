/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.typed.client.http;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.javaagent.typed.client.ClientTypedTracer;
import io.opentelemetry.trace.TracingContextUtils;

public abstract class HttpClientTypedTracer<
        T extends HttpClientTypedSpan<T, REQUEST, RESPONSE>, REQUEST, RESPONSE>
    extends ClientTypedTracer<T, REQUEST, RESPONSE> {

  @Override
  protected T startSpan(REQUEST request, T span) {
    Context context = TracingContextUtils.withSpan(span, Context.current());
    OpenTelemetry.getPropagators().getTextMapPropagator().inject(context, request, getSetter());
    return super.startSpan(request, span);
  }

  protected abstract TextMapPropagator.Setter<REQUEST> getSetter();
}
