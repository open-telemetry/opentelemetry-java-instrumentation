/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.typed.server.http;

import static io.opentelemetry.instrumentation.api.decorator.BaseDecorator.extract;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.javaagent.typed.server.ServerTypedTracer;

public abstract class HttpServerTypedTracer<
        T extends HttpServerTypedSpan<T, REQUEST, RESPONSE>, REQUEST, RESPONSE>
    extends ServerTypedTracer<T, REQUEST, RESPONSE> {

  @Override
  protected Span.Builder buildSpan(REQUEST request, Span.Builder spanBuilder) {
    spanBuilder.setParent(extract(request, getGetter()));
    return super.buildSpan(request, spanBuilder);
  }

  protected abstract TextMapPropagator.Getter<REQUEST> getGetter();
}
