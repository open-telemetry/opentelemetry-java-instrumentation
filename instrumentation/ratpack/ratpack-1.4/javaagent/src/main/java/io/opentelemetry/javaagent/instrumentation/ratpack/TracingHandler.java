/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack;

import static io.opentelemetry.javaagent.instrumentation.ratpack.RatpackSingletons.instrumenter;
import static io.opentelemetry.javaagent.instrumentation.ratpack.RatpackSingletons.updateServerSpanName;
import static io.opentelemetry.javaagent.instrumentation.ratpack.RatpackSingletons.updateSpanNames;

import io.netty.util.Attribute;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.instrumentation.netty.v4_1.AttributeKeys;
import ratpack.handling.Context;
import ratpack.handling.Handler;

public final class TracingHandler implements Handler {

  private static final String INITIAL_SPAN_NAME = "ratpack.handler";

  public static final Handler INSTANCE = new TracingHandler();

  @Override
  public void handle(Context ctx) {
    Attribute<io.opentelemetry.context.Context> spanAttribute =
        ctx.getDirectChannelAccess().getChannel().attr(AttributeKeys.SERVER_CONTEXT);
    io.opentelemetry.context.Context serverSpanContext = spanAttribute.get();

    // Must use context from channel, as executor instrumentation is not accurate - Ratpack
    // internally queues events and then drains them in batches, causing executor instrumentation to
    // attach the same context to a batch of events from different requests.
    io.opentelemetry.context.Context parentOtelContext =
        serverSpanContext != null ? serverSpanContext : Java8BytecodeBridge.currentContext();
    io.opentelemetry.context.Context callbackContext;

    if (instrumenter().shouldStart(parentOtelContext, INITIAL_SPAN_NAME)) {
      io.opentelemetry.context.Context otelContext =
          instrumenter().start(parentOtelContext, INITIAL_SPAN_NAME);
      ctx.getExecution().add(otelContext);
      ctx.getResponse()
          .beforeSend(
              response -> {
                updateSpanNames(otelContext, ctx);
                instrumenter().end(otelContext, INITIAL_SPAN_NAME, null, null);
              });
      callbackContext = otelContext;
    } else {
      // just update the server span name
      ctx.getResponse().beforeSend(response -> updateServerSpanName(parentOtelContext, ctx));
      callbackContext = parentOtelContext;
    }

    try (Scope ignored = callbackContext.makeCurrent()) {
      ctx.next();
      // exceptions are captured by ServerErrorHandlerInstrumentation
    }
  }
}
