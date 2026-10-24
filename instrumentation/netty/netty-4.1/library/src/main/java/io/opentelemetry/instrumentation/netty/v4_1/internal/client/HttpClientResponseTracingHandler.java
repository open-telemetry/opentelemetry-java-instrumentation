/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1.internal.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.NettyCommonRequest;
import io.opentelemetry.instrumentation.netty.v4_1.internal.ProtocolEventHandler;
import io.opentelemetry.instrumentation.netty.v4_1.internal.ProtocolSpecificEvent;
import io.opentelemetry.instrumentation.netty.v4_1.internal.client.ClientContexts.RequestState;
import java.nio.channels.ClosedChannelException;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class HttpClientResponseTracingHandler extends ChannelInboundHandlerAdapter {

  private final Instrumenter<NettyCommonRequest, HttpResponse> instrumenter;
  private final ProtocolEventHandler protocolEventHandler;

  public HttpClientResponseTracingHandler(
      Instrumenter<NettyCommonRequest, HttpResponse> instrumenter,
      ProtocolEventHandler protocolEventHandler) {
    this.instrumenter = instrumenter;
    this.protocolEventHandler = protocolEventHandler;
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    ClientContexts contexts = ClientContexts.get(ctx.channel());
    RequestState state;
    boolean complete = false;
    boolean switchingProtocols = false;
    synchronized (contexts) {
      state = contexts.peek();
      if (state != null) {
        if (msg instanceof HttpResponse) {
          HttpResponse response = (HttpResponse) msg;
          switchingProtocols = response.status().equals(HttpResponseStatus.SWITCHING_PROTOCOLS);
          state.response = response;
          state.waitingForFinalResponse = response.status().code() < 200;
          if (state.waitingForFinalResponse && !switchingProtocols) {
            state.response = null;
          }
          if (switchingProtocols) {
            // Finagle transfers the upgrade request to its HTTP/2 stream.
            state.upgraded = true;
          }
          if (msg instanceof FullHttpResponse && switchingProtocols) {
            // A full 101 is an event, not the eventual response on an upgraded HTTP/2 stream.
            state.response = null;
          }
        }
        if ((msg instanceof FullHttpResponse || msg instanceof LastHttpContent)
            && !state.waitingForFinalResponse) {
          contexts.remove(state);
          complete = true;
        }
      }
    }
    if (state == null) {
      super.channelRead(ctx, msg);
      return;
    }
    if (switchingProtocols && state.context != null) {
      protocolEventHandler.handle(
          ProtocolSpecificEvent.SWITCHING_PROTOCOLS,
          state.context,
          state.request.getRequest(),
          (HttpResponse) msg);
    }
    if (complete) {
      state.end(instrumenter, null);
    }
    // We want the callback in the scope of the parent, not the client span
    ClientContexts.ResponseCallbackScope previousCallback =
        complete ? ClientContexts.beginResponseCallback(state, msg) : null;
    try (Scope ignored = state.parent.makeCurrent()) {
      super.channelRead(ctx, msg);
    } finally {
      if (complete) {
        ClientContexts.endResponseCallback(previousCallback);
      }
      contexts.publish(ctx.channel());
    }
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    Context parent = ClientContexts.get(ctx.channel()).callbackParent();
    ClientContexts.close(ctx.channel(), instrumenter, new ClosedChannelException());
    if (parent == null) {
      super.channelInactive(ctx);
    } else {
      try (Scope ignored = parent.makeCurrent()) {
        super.channelInactive(ctx);
      }
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    ClientContexts contexts = ClientContexts.get(ctx.channel());
    ClientContexts.fail(ctx.channel(), instrumenter, cause);
    Context parent = contexts.exceptionParent(cause);
    if (parent == null) {
      super.exceptionCaught(ctx, cause);
    } else {
      try (Scope ignored = parent.makeCurrent()) {
        super.exceptionCaught(ctx, cause);
      }
    }
  }
}
