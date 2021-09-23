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
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import ratpack.handling.Context;
import ratpack.handling.Handler;

public final class TracingHandler implements Handler {
  public static final Handler INSTANCE = new TracingHandler();

  @Override
  public void handle(Context ctx) {
    Attribute<io.opentelemetry.context.Context> spanAttribute =
        ctx.getDirectChannelAccess().getChannel().attr(AttributeKeys.SERVER_CONTEXT);
    io.opentelemetry.context.Context serverSpanContext = spanAttribute.get();

    // Must use context from channel, as executor instrumentation is not accurate - Ratpack
    // internally queues events and then drains them in batches, causing executor instrumentation to
    // attach the same context to a batch of events from different requests.
    io.opentelemetry.context.Context parentContext =
        serverSpanContext != null ? serverSpanContext : Java8BytecodeBridge.currentContext();

    io.opentelemetry.context.Context ratpackContext =
        tracer().startSpan(parentContext, "ratpack.handler", SpanKind.INTERNAL);
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
