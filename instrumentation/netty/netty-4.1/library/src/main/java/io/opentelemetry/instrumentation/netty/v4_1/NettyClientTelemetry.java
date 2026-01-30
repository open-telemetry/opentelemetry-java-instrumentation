/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOutboundHandler;
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
  public ChannelOutboundHandler createRequestHandler() {
    return handlerFactory.createRequestHandler();
  }

  /**
   * Returns a handler that instruments incoming HTTP responses. Must be paired with {@link
   * #createRequestHandler()}.
   */
  public ChannelInboundHandler createResponseHandler() {
    return handlerFactory.createResponseHandler();
  }

  /**
   * Returns a handler that instruments outgoing HTTP requests and incoming responses in a single
   * handler.
   */
  public CombinedChannelDuplexHandler<ChannelInboundHandler, ChannelOutboundHandler>
      createCombinedHandler() {
    return handlerFactory.createCombinedHandler();
  }

  /**
   * Propagates the {@link Context} to the {@link Channel}. Must be called before each HTTP request
   * on the channel.
   */
  public static void setParentContext(Channel channel, Context parentContext) {
    channel.attr(AttributeKeys.CLIENT_PARENT_CONTEXT).set(parentContext);
  }

  /**
   * Propagates the {@link Context} to the {@link Channel}. Must be called before each HTTP request
   * on the channel.
   *
   * @deprecated Use {@link #setParentContext(Channel, Context)} instead.
   */
  @Deprecated
  public static void setChannelContext(Channel channel, Context context) {
    setParentContext(channel, context);
  }
}
