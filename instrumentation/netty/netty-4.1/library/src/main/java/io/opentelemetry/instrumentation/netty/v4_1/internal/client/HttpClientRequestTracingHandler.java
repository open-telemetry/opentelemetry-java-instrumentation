/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1.internal.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.NettyCommonRequest;
import io.opentelemetry.instrumentation.netty.v4_1.internal.client.ClientContexts.RequestState;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class HttpClientRequestTracingHandler extends ChannelOutboundHandlerAdapter {

  public static final AttributeKey<NettyCommonRequest> HTTP_CLIENT_REQUEST =
      AttributeKey.valueOf(HttpClientRequestTracingHandler.class, "http-client-request");

  private final Instrumenter<NettyCommonRequest, HttpResponse> instrumenter;

  public HttpClientRequestTracingHandler(
      Instrumenter<NettyCommonRequest, HttpResponse> instrumenter) {
    this.instrumenter = instrumenter;
  }

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise prm) throws Exception {
    ClientContexts contexts = ClientContexts.get(ctx.channel());
    if (!(msg instanceof HttpRequest)) {
      writeAndTrackFailure(ctx, msg, prm, contexts);
      return;
    }

    Context parentContext = contexts.parent(ctx.channel());
    if (!contexts.isUsable()) {
      super.write(ctx, msg, prm);
      return;
    }
    NettyCommonRequest request = NettyCommonRequest.create((HttpRequest) msg, ctx.channel());
    Context context =
        !isAwsRequest(request) && instrumenter.shouldStart(parentContext, request)
            ? instrumenter.start(parentContext, request)
            : null;
    RequestState state = new RequestState(parentContext, context, request);
    if (!contexts.add(ctx.channel(), state)) {
      state.end(instrumenter, contexts.failure());
      super.write(ctx, msg, prm);
      return;
    }
    try (Scope ignored = (context == null ? parentContext : context).makeCurrent()) {
      writeAndTrackFailure(ctx, msg, prm, contexts);
    }
    // span is ended normally in HttpClientResponseTracingHandler
  }

  private void writeAndTrackFailure(
      ChannelHandlerContext ctx, Object msg, ChannelPromise prm, ClientContexts contexts)
      throws Exception {
    if (contexts.peek() == null) {
      super.write(ctx, msg, prm);
      return;
    }
    ChannelPromise promise = prm.unvoid();
    promise.addListener(
        future -> {
          if (!future.isSuccess()) {
            contexts.fail(ctx.channel(), instrumenter, future.cause());
          }
        });
    try {
      super.write(ctx, msg, promise);
    } catch (Throwable t) {
      contexts.fail(ctx.channel(), instrumenter, t);
      throw t;
    }
  }

  private static boolean isAwsRequest(NettyCommonRequest request) {
    // The AWS SDK uses Netty for asynchronous clients but constructs a request signature before
    // beginning transport. This means we MUST suppress Netty spans we would normally create or
    // they will inject their own trace header, which does not match what was present when the
    // signature was computed, breaking the SDK request completely. We have not found how to
    // cleanly propagate context from the SDK instrumentation, which executes on an application
    // thread, to Netty instrumentation, which executes on event loops. If it's possible, it may
    // require instrumenting internal classes. Using a header which is more or less guaranteed to
    // always exist is arguably more stable.
    return request.getRequest().headers().contains("amz-sdk-invocation-id");
  }
}
