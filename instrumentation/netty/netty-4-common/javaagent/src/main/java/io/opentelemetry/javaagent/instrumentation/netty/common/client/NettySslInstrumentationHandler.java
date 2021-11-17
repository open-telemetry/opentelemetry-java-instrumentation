/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common.client;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.opentelemetry.context.Context;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.SocketAddress;

// inspired by reactor-netty SslProvider.SslReadHandler
public final class NettySslInstrumentationHandler extends ChannelDuplexHandler {

  private static final Class<?> SSL_HANDSHAKE_COMPLETION_EVENT;
  private static final MethodHandle GET_CAUSE;

  static {
    Class<?> sslHandshakeCompletionEvent = null;
    MethodHandle getCause = null;
    try {
      sslHandshakeCompletionEvent =
          Class.forName(
              "io.netty.handler.ssl.SslHandshakeCompletionEvent",
              false,
              NettySslInstrumentationHandler.class.getClassLoader());
      getCause =
          MethodHandles.lookup()
              .findVirtual(
                  sslHandshakeCompletionEvent, "cause", MethodType.methodType(Throwable.class));
    } catch (Throwable t) {
      // no SSL classes on classpath
    }
    SSL_HANDSHAKE_COMPLETION_EVENT = sslHandshakeCompletionEvent;
    GET_CAUSE = getCause;
  }

  private final NettySslInstrumenter instrumenter;
  private Context parentContext;
  private NettySslRequest request;
  private Context context;

  public NettySslInstrumentationHandler(NettySslInstrumenter instrumenter) {
    this.instrumenter = instrumenter;
  }

  @Override
  public void channelRegistered(ChannelHandlerContext ctx) {
    // this should never happen at this point (since the handler is only registered when SSL classes
    // are on classpath); checking just to be extra safe
    if (SSL_HANDSHAKE_COMPLETION_EVENT == null) {
      ctx.pipeline().remove(this);
      ctx.fireChannelRegistered();
      return;
    }

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
    if (SSL_HANDSHAKE_COMPLETION_EVENT.isInstance(evt)) {
      if (ctx.pipeline().context(this) != null) {
        ctx.pipeline().remove(this);
      }

      if (context != null) {
        instrumenter.end(context, request, getCause(evt));
      }
    }

    ctx.fireUserEventTriggered(evt);
  }

  private static Throwable getCause(Object evt) {
    try {
      return (Throwable) GET_CAUSE.invoke(evt);
    } catch (Throwable e) {
      // should not ever happen
      return null;
    }
  }
}
