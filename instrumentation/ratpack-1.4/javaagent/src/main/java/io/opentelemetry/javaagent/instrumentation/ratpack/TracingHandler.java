/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack;

import static io.opentelemetry.javaagent.instrumentation.ratpack.RatpackTracer.tracer;

import io.netty.util.Attribute;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.netty.v4_1.AttributeKeys;
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
    io.opentelemetry.context.Context ratpackContext =
        tracer().startSpan("ratpack.handler", SpanKind.INTERNAL);
    ctx.getExecution().add(ratpackContext);

    ctx.getResponse()
        .beforeSend(
            response -> {
              if (serverSpanContext != null) {
                // Rename the netty span name with the ratpack route.
                tracer().onContext(serverSpanContext, ctx);
              }
              tracer().onContext(ratpackContext, ctx);
              tracer().end(ratpackContext);
            });

    try (Scope ignored = ratpackContext.makeCurrent()) {
      ctx.next();
      // exceptions are captured by ServerErrorHandlerInstrumentation
    }
  }
}
