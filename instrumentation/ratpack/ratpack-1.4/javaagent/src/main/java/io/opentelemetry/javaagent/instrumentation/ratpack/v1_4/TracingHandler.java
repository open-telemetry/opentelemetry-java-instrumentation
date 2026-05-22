/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack.v1_4;

import static io.opentelemetry.javaagent.instrumentation.ratpack.v1_4.RatpackSingletons.instrumenter;

import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.netty.v4_1.internal.ServerContext;
import io.opentelemetry.instrumentation.netty.v4_1.internal.ServerContexts;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import ratpack.handling.Context;
import ratpack.handling.Handler;

public class TracingHandler implements Handler {

  private static final String INITIAL_SPAN_NAME = "ratpack.handler";

  @Override
  public void handle(Context ctx) {
    ServerContext serverContext =
        ServerContexts.peekFirst(ctx.getDirectChannelAccess().getChannel());

    // Must use context from channel, as executor instrumentation is not accurate - Ratpack
    // internally queues events and then drains them in batches, causing executor instrumentation to
    // attach the same context to a batch of events from different requests.
    io.opentelemetry.context.Context parentOtelContext =
        serverContext != null ? serverContext.context() : Java8BytecodeBridge.currentContext();
    io.opentelemetry.context.Context callbackContext;

    if (instrumenter().shouldStart(parentOtelContext, INITIAL_SPAN_NAME)) {
      io.opentelemetry.context.Context otelContext =
          instrumenter().start(parentOtelContext, INITIAL_SPAN_NAME);
      ctx.getExecution().add(otelContext);
      ctx.getResponse()
          .beforeSend(
              response -> {
                RatpackSingletons.updateSpanNames(otelContext, ctx);
                instrumenter().end(otelContext, INITIAL_SPAN_NAME, null, null);
              });
      callbackContext = otelContext;
    } else {
      // just update the server span name
      ctx.getResponse()
          .beforeSend(response -> RatpackSingletons.updateServerSpanName(parentOtelContext, ctx));
      callbackContext = parentOtelContext;
    }

    try (Scope ignored = callbackContext.makeCurrent()) {
      ctx.next();
      // exceptions are captured by ServerErrorHandlerInstrumentation
    }
  }
}
