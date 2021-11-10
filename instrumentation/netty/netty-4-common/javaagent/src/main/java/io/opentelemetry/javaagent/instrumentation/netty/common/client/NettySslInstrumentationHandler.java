/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common.client;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.opentelemetry.context.Context;
import java.net.SocketAddress;

// inspired by reactor-netty SslProvider.SslReadHandler
public final class NettySslInstrumentationHandler extends ChannelDuplexHandler {

  private final NettySslInstrumenter instrumenter;
  private Context parentContext;
  private NettySslRequest request;
  private Context context;

  public NettySslInstrumentationHandler(NettySslInstrumenter instrumenter) {
    this.instrumenter = instrumenter;
  }

  @Override
  public void channelRegistered(ChannelHandlerContext ctx) {
    // remember the parent context from the time of channel registration;
    // this happens inside Bootstrap#connect()
    parentContext = Context.current();
    ctx.fireChannelRegistered();
  }

  @Override
  public void connect(
      ChannelHandlerContext ctx,
      SocketAddress remoteAddress,
      SocketAddress localAddress,
      ChannelPromise promise) {

    // netty SslHandler starts the handshake after it receives the channelActive() signal; this
    // happens just after the connection is established
    // this makes connect() promise a good place to start the SSL handshake span
    promise.addListener(
        future -> {
          // there won't be any SSL handshake if the channel fails to connect
          if (!future.isSuccess()) {
            return;
          }
          request = NettySslRequest.create(ctx.channel());
          if (instrumenter.shouldStart(parentContext, request)) {
            context = instrumenter.start(parentContext, request);
          }
        });
    ctx.connect(remoteAddress, localAddress, promise);
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
    if (evt instanceof SslHandshakeCompletionEvent) {
      if (ctx.pipeline().context(this) != null) {
        ctx.pipeline().remove(this);
      }

      SslHandshakeCompletionEvent handshake = (SslHandshakeCompletionEvent) evt;
      if (context != null) {
        instrumenter.end(context, request, handshake.cause());
      }
    }

    ctx.fireUserEventTriggered(evt);
  }
}
