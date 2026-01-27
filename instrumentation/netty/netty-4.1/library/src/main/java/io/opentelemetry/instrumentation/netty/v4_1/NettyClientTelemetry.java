/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.CombinedChannelDuplexHandler;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.NettyCommonRequest;
import io.opentelemetry.instrumentation.netty.v4_1.internal.AttributeKeys;
import io.opentelemetry.instrumentation.netty.v4_1.internal.client.NettyClientHandlerFactory;

/** Entrypoint for instrumenting Netty HTTP clients. */
public final class NettyClientTelemetry {

  private final NettyClientHandlerFactory handlerFactory;

  NettyClientTelemetry(
      Instrumenter<NettyCommonRequest, HttpResponse> instrumenter,
      boolean emitExperimentalHttpClientEvents) {
    this.handlerFactory =
        new NettyClientHandlerFactory(instrumenter, emitExperimentalHttpClientEvents);
  }

  /** Returns a new instance configured with the given {@link OpenTelemetry} instance. */
  public static NettyClientTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /** Returns a builder configured with the given {@link OpenTelemetry} instance. */
  public static NettyClientTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new NettyClientTelemetryBuilder(openTelemetry);
  }

  /**
   * Returns a handler that instruments outgoing HTTP requests. Must be paired with {@link
   * #createResponseHandler()}.
   */
  public ChannelOutboundHandlerAdapter createRequestHandler() {
    return handlerFactory.createRequestHandler();
  }

  /**
   * Returns a handler that instruments incoming HTTP responses. Must be paired with {@link
   * #createRequestHandler()}.
   */
  public ChannelInboundHandlerAdapter createResponseHandler() {
    return handlerFactory.createResponseHandler();
  }

  /**
   * Returns a handler that instruments outgoing HTTP requests and incoming responses in a single
   * handler.
   */
  public CombinedChannelDuplexHandler<ChannelInboundHandlerAdapter, ChannelOutboundHandlerAdapter>
      createCombinedHandler() {
    return handlerFactory.createCombinedHandler();
  }

  /**
   * Propagates the {@link Context} to the {@link Channel}. Must be called before each HTTP request
   * on the channel.
   */
  // TODO (trask) rename to setParentContext()?
  public static void setChannelContext(Channel channel, Context context) {
    channel.attr(AttributeKeys.CLIENT_PARENT_CONTEXT).set(context);
  }
}
