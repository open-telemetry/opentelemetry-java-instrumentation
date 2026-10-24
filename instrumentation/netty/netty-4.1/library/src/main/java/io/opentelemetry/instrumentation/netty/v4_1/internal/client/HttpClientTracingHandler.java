/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1.internal.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPromise;
import io.netty.channel.CombinedChannelDuplexHandler;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.NettyCommonRequest;
import io.opentelemetry.instrumentation.netty.v4_1.internal.ProtocolEventHandler;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class HttpClientTracingHandler
    extends CombinedChannelDuplexHandler<ChannelInboundHandler, ChannelOutboundHandler> {
  private final Instrumenter<NettyCommonRequest, HttpResponse> instrumenter;

  public HttpClientTracingHandler(
      Instrumenter<NettyCommonRequest, HttpResponse> instrumenter,
      ProtocolEventHandler protocolEventHandler) {
    super(
        new HttpClientResponseTracingHandler(instrumenter, protocolEventHandler),
        new HttpClientRequestTracingHandler(instrumenter));
    this.instrumenter = instrumenter;
  }

  @Override
  public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
    Context parent = ClientContexts.get(ctx.channel()).callbackParent();
    ClientContexts.close(ctx.channel(), instrumenter, null);
    if (parent != null) {
      try (Scope ignored = parent.makeCurrent()) {
        super.close(ctx, promise);
      }
    } else {
      super.close(ctx, promise);
    }
  }
}
