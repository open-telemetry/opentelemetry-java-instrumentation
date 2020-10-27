/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack;

import static io.opentelemetry.javaagent.instrumentation.ratpack.RatpackTracer.TRACER;

import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Kind;
import ratpack.handling.Context;
import ratpack.handling.Handler;

public final class TracingHandler implements Handler {
  public static final Handler INSTANCE = new TracingHandler();

  /**
   * This constant is copied over from
   * io.opentelemetry.javaagent.instrumentation.netty.v4_1.AttributeKeys. The key string must be
   * kept consistent.
   */
  public static final AttributeKey<io.opentelemetry.context.Context> SERVER_ATTRIBUTE_KEY =
      AttributeKey.valueOf(
          "io.opentelemetry.javaagent.instrumentation.netty.v4_1.server.HttpServerTracingHandler.context");

  @Override
  public void handle(Context ctx) {
    Attribute<io.opentelemetry.context.Context> spanAttribute =
        ctx.getDirectChannelAccess().getChannel().attr(SERVER_ATTRIBUTE_KEY);
    io.opentelemetry.context.Context serverSpanContext = spanAttribute.get();

    // Relying on executor instrumentation to assume the netty span is in context as the parent.
    Span ratpackSpan = TRACER.startSpan("ratpack.handler", Kind.INTERNAL);
    ctx.getExecution().add(ratpackSpan);

    ctx.getResponse()
        .beforeSend(
            response -> {
              if (serverSpanContext != null) {
                // Rename the netty span name with the ratpack route.
                TRACER.onContext(Span.fromContext(serverSpanContext), ctx);
              }
              TRACER.onContext(ratpackSpan, ctx);
              TRACER.end(ratpackSpan);
            });

    try (Scope ignored = ratpackSpan.makeCurrent()) {
      ctx.next();
      // exceptions are captured by ServerErrorHandlerInstrumentation
    }
  }
}
