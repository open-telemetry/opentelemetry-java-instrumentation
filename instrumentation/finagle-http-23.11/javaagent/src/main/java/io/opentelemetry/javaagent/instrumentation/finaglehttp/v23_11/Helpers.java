/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11;

import static io.opentelemetry.instrumentation.netty.v4_1.internal.client.HttpClientRequestTracingHandler.HTTP_CLIENT_REQUEST;
import static io.opentelemetry.javaagent.instrumentation.netty.v4_1.NettyClientSingletons.clientHandlerFactory;

import com.twitter.finagle.ChannelTransportHelpers;
import com.twitter.finagle.Netty4HttpPackageHelpers;
import com.twitter.finagle.http.Request;
import com.twitter.finagle.http.Request$;
import com.twitter.finagle.http.collection.RecordSchema;
import com.twitter.finagle.http2.transport.common.Http2StreamMessageHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.OpenTelemetryChannelInitializerDelegate;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http2.Http2StreamFrameToHttpObjectCodec;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.netty.v4_1.internal.AttributeKeys;
import io.opentelemetry.instrumentation.netty.v4_1.internal.ServerContexts;
import io.opentelemetry.instrumentation.netty.v4_1.internal.client.HttpClientTracingHandler;
import io.opentelemetry.instrumentation.netty.v4_1.internal.server.HttpServerTracingHandler;
import io.opentelemetry.javaagent.instrumentation.netty.common.v4_0.VirtualFieldHelper;
import io.opentelemetry.javaagent.instrumentation.netty.v4_1.NettyServerSingletons;

public class Helpers {

  private static final VirtualField<FullHttpRequest, Context> FULL_HTTP_REQUEST_CONTEXT = VirtualField.find(
      FullHttpRequest.class, Context.class);
  private static final VirtualField<HttpRequest, Context> HTTP_REQUEST_CONTEXT = VirtualField.find(
      HttpRequest.class, Context.class);

  public static final RecordSchema.Field<Context> OTEL_CONTEXT_KEY =
      Request$.MODULE$.Schema().newField();

  public static final String OTEL_NETTY_HANDLER = "otelFinagleNettyHandler";

  private Helpers() {}

  /*
  Bridges the netty instrumentation to the finagle-netty integration.
   */
  public static <C extends Channel> ChannelInitializer<C> wrapServer(ChannelInitializer<C> inner) {
    return new OpenTelemetryChannelInitializerDelegate<C>(inner) {

      @Override
      protected void initChannel(C channel) throws Exception {
        // do all the needful up front, as this may add necessary handlers -- see below
        super.initChannel(channel);

        // the parent channel is the original http/1.1 channel and has the contexts stored in it;
        // we assign to this new channel as the old one will not be evaluated in the upgraded h2c
        // chain
        ServerContexts serverContexts = ServerContexts.get(channel.parent());
        channel.attr(AttributeKeys.SERVER_CONTEXTS).set(serverContexts);

        // todo add way to propagate the protocol version override up to the netty instrumentation;
        //  why: the netty instrumentation extracts the http protocol version from the HttpRequest
        //  object which in this case is _always_ http/1.1 due to the use of this adapter codec,
        //  Http2StreamFrameToHttpObjectCodec
        ChannelHandlerContext codecCtx =
            channel.pipeline().context(Http2StreamFrameToHttpObjectCodec.class);
        if (codecCtx != null) {
          if (channel.pipeline().get(HttpServerTracingHandler.class) == null) {

            ChannelHandler ourHandler = NettyServerSingletons.createCombinedHandler();

            channel
                .pipeline()
                .addAfter(codecCtx.name(), ourHandler.getClass().getName(), ourHandler);
            // attach this in this way to match up with how netty instrumentation expects things
            VirtualFieldHelper.CHANNEL_HANDLER.set(codecCtx.handler(), ourHandler);
          }
        }
      }
    };
  }

  /*
  Bridges the netty instrumentation to the finagle-netty integration (for h2).
   */
  public static <C extends Channel> ChannelInitializer<C> wrapClient(ChannelInitializer<C> inner) {
    return new OpenTelemetryChannelInitializerDelegate<C>(inner) {

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
            ChannelHandler ourHandler = clientHandlerFactory().createCombinedHandler();

            channel
                .pipeline()
                .addAfter(codecCtx.name(), ourHandler.getClass().getName(), ourHandler);
            // attach this in this way to match up with how netty instrumentation expects things
            VirtualFieldHelper.CHANNEL_HANDLER.set(codecCtx.handler(), ourHandler);
          }
        }
      }
    };
  }

  /*
  Part 1/3 of bridging the otel Context from netty to finagle (for h2).
   */
  public static void mutateHandlerPipeline(Channel ch) {
    ChannelHandler h1Handler = ch.pipeline().get(Netty4HttpPackageHelpers.getHttpCodecName());
    Http2StreamMessageHandler h2Handler = ch.pipeline().get(Http2StreamMessageHandler.class);

    // h1 server handler || h2 server handler;
    // private class on a semi-private type -- not bothering to extract that any other way
    if (
        h1Handler instanceof HttpServerCodec || (h2Handler != null && h2Handler.getClass().getName()
            .equals(
                "com.twitter.finagle.http2.transport.common.Http2StreamMessageHandler$ServerHttp2StreamMessageHandler"))) {
      // ensure we capture the server context and assign it to the outgoing request before offering
      // to the AsyncQueue;
      // not applicable to clients
      ch.pipeline()
          .addBefore(
              ChannelTransportHelpers.getHandlerName(),
              OTEL_NETTY_HANDLER,
              new ChannelInboundHandlerAdapter() {
                /*
                Assign the context to the request.

                Part 1/3 for chaining the context from netty to finagle.
                 */
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                  // type switch courtesy of
                  // com.twitter.finagle.netty4.http.Netty4ServerStreamTransport.read()
                  if (msg instanceof FullHttpRequest) {
                    FULL_HTTP_REQUEST_CONTEXT.set((FullHttpRequest) msg, Context.current());
                  } else if (msg instanceof HttpRequest) {
                    HTTP_REQUEST_CONTEXT.set((HttpRequest) msg, Context.current());
                  } else {
                    throw new IllegalArgumentException("unexpected request type: " + msg);
                  }

                  super.channelRead(ctx, msg);
                }
              });
    }
  }

  /*
  Part 2/3 of bridging the otel Context from netty to finagle.
   */
  public static void chainContextToFinagle(Object msg, Request request) {
    Context context;
    // type switch courtesy of com.twitter.finagle.netty4.http.Netty4ServerStreamTransport.read()
    if (msg instanceof FullHttpRequest) {
      context = FULL_HTTP_REQUEST_CONTEXT.get((FullHttpRequest) msg);
    } else if (msg instanceof HttpRequest) {
      context = HTTP_REQUEST_CONTEXT.get((HttpRequest) msg);
    } else {
      // shouldn't practically reach here
      throw new IllegalArgumentException("unexpected request type: " + msg);
    }

    // hook the Context from netty's request up to finagle's request
    request.ctx().updateAndLock(OTEL_CONTEXT_KEY, context);
  }
}
