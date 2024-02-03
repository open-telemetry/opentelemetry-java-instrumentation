/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.v23_11;

import static io.opentelemetry.instrumentation.netty.v4_1.internal.client.HttpClientRequestTracingHandler.HTTP_CLIENT_REQUEST;

import com.twitter.util.Local;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelInitializerDelegate;
import io.netty.handler.codec.http2.Http2StreamFrameToHttpObjectCodec;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.netty.v4_1.internal.AttributeKeys;
import io.opentelemetry.instrumentation.netty.v4_1.internal.ServerContext;
import io.opentelemetry.instrumentation.netty.v4_1.internal.client.HttpClientTracingHandler;
import io.opentelemetry.instrumentation.netty.v4_1.internal.server.HttpServerTracingHandler;
import io.opentelemetry.javaagent.instrumentation.netty.v4_1.NettyClientSingletons;
import io.opentelemetry.javaagent.instrumentation.netty.v4_1.NettyHttpServerResponseBeforeCommitHandler;
import io.opentelemetry.javaagent.instrumentation.netty.v4_1.NettyServerSingletons;
import java.util.Deque;

public class Helpers {

  private Helpers() {}

  public static final Local<Context> CONTEXT_LOCAL = new Local<>();

  public static <C extends Channel> ChannelInitializer<C> wrapServer(ChannelInitializer<C> inner) {
    return new ChannelInitializerDelegate<C>(inner) {

      @Override
      protected void initChannel(C channel) throws Exception {
        // do all the needful up front, as this may add necessary handlers -- see below
        super.initChannel(channel);

        // the parent channel is the original http/1.1 channel and has the contexts stored in it;
        // we assign to this new channel as the old one will not be evaluated in the upgraded h2c
        // chain
        Deque<ServerContext> serverContexts =
            channel.parent().attr(AttributeKeys.SERVER_CONTEXT).get();
        channel.attr(AttributeKeys.SERVER_CONTEXT).set(serverContexts);

        // todo add way to propagate the protocol version override up to the netty instrumentation;
        //  why: the netty instrumentation extracts the http protocol version from the HttpRequest
        //  object which in this case is _always_ http/1.1 due to the use of this adapter codec,
        //  Http2StreamFrameToHttpObjectCodec
        ChannelHandlerContext codecCtx =
            channel.pipeline().context(Http2StreamFrameToHttpObjectCodec.class);
        if (codecCtx != null) {
          if (channel.pipeline().get(HttpServerTracingHandler.class) == null) {
            VirtualField<ChannelHandler, ChannelHandler> virtualField =
                VirtualField.find(ChannelHandler.class, ChannelHandler.class);
            ChannelHandler ourHandler =
                NettyServerSingletons.serverTelemetry()
                    .createCombinedHandler(NettyHttpServerResponseBeforeCommitHandler.INSTANCE);

            channel
                .pipeline()
                .addAfter(codecCtx.name(), ourHandler.getClass().getName(), ourHandler);
            // attach this in this way to match up with how netty instrumentation expects things
            virtualField.set(codecCtx.handler(), ourHandler);
          }
        }
      }
    };
  }

  public static <C extends Channel> ChannelInitializer<C> wrapClient(ChannelInitializer<C> inner) {
    return new ChannelInitializerDelegate<C>(inner) {

      // wraps everything for roughly the same reasons as in wrapServer(), above
      @Override
      protected void initChannel(C channel) throws Exception {
        super.initChannel(channel);

        channel
            .attr(AttributeKeys.CLIENT_PARENT_CONTEXT)
            .set(channel.parent().attr(AttributeKeys.CLIENT_PARENT_CONTEXT).get());
        channel
            .attr(AttributeKeys.CLIENT_CONTEXT)
            .set(channel.parent().attr(AttributeKeys.CLIENT_CONTEXT).get());
        channel.attr(HTTP_CLIENT_REQUEST).set(channel.parent().attr(HTTP_CLIENT_REQUEST).get());

        // todo add way to propagate the protocol version override up to the netty instrumentation;
        //  why: the netty instrumentation extracts the http protocol version from the HttpRequest
        //  object which in this case is _always_ http/1.1 due to the use of this adapter codec,
        //  Http2StreamFrameToHttpObjectCodec
        ChannelHandlerContext codecCtx =
            channel.pipeline().context(Http2StreamFrameToHttpObjectCodec.class);
        if (codecCtx != null) {
          if (channel.pipeline().get(HttpClientTracingHandler.class) == null) {
            VirtualField<ChannelHandler, ChannelHandler> virtualField =
                VirtualField.find(ChannelHandler.class, ChannelHandler.class);
            ChannelHandler ourHandler =
                NettyClientSingletons.clientTelemetry().createCombinedHandler();

            channel
                .pipeline()
                .addAfter(codecCtx.name(), ourHandler.getClass().getName(), ourHandler);
            // attach this in this way to match up with how netty instrumentation expects things
            virtualField.set(codecCtx.handler(), ourHandler);
          }
        }
      }
    };
  }
}
