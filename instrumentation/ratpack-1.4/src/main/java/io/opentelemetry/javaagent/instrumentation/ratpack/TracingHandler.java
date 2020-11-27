/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack;

import static io.opentelemetry.javaagent.instrumentation.ratpack.RatpackTracer.tracer;

import io.netty.util.Attribute;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Span.Kind;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.netty.v4_1.AttributeKeys;
import ratpack.handling.Context;
import ratpack.handling.Handler;

public final class TracingHandler implements Handler {
  public static final Handler INSTANCE = new TracingHandler();

  @Override
  public void handle(Context ctx) {
    Attribute<io.opentelemetry.context.Context> spanAttribute =
        ctx.getDirectChannelAccess().getChannel().attr(AttributeKeys.SERVER_SPAN);
    io.opentelemetry.context.Context serverSpanContext = spanAttribute.get();

    // Relying on executor instrumentation to assume the netty span is in context as the parent.
    Span ratpackSpan = tracer().startSpan("ratpack.handler", Kind.INTERNAL);
    ctx.getExecution().add(ratpackSpan);

    ctx.getResponse()
        .beforeSend(
            response -> {
              if (serverSpanContext != null) {
                // Rename the netty span name with the ratpack route.
                tracer().onContext(Span.fromContext(serverSpanContext), ctx);
              }
              tracer().onContext(ratpackSpan, ctx);
              tracer().end(ratpackSpan);
            });

    try (Scope ignored = ratpackSpan.makeCurrent()) {
      ctx.next();
      // exceptions are captured by ServerErrorHandlerInstrumentation
    }
  }
}
