/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1.client;

import static io.opentelemetry.javaagent.instrumentation.netty.v4_1.client.NettyHttpClientTracer.tracer;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.Operation;
import io.opentelemetry.javaagent.instrumentation.netty.v4_1.AttributeKeys;

public class HttpClientRequestTracingHandler extends ChannelOutboundHandlerAdapter {

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise prm) {
    Operation operation = tracer().startOperation(ctx, msg);
    ctx.channel().attr(AttributeKeys.CLIENT_OPERATION).set(operation);
    try (Scope ignored = operation.makeCurrent()) {
      ctx.write(msg, prm);
      // span is ended normally in HttpClientResponseTracingHandler
    } catch (Throwable throwable) {
      tracer().endExceptionally(operation, throwable);
      throw throwable;
    }
  }
}
