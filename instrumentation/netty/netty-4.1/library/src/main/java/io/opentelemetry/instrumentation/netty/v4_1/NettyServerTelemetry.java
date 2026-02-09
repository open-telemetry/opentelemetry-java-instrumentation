/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1;

import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.CombinedChannelDuplexHandler;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.NettyCommonRequest;
import io.opentelemetry.instrumentation.netty.v4_1.internal.ProtocolEventHandler;
import io.opentelemetry.instrumentation.netty.v4_1.internal.server.HttpServerRequestTracingHandler;
import io.opentelemetry.instrumentation.netty.v4_1.internal.server.HttpServerResponseBeforeCommitHandler;
import io.opentelemetry.instrumentation.netty.v4_1.internal.server.HttpServerResponseTracingHandler;
import io.opentelemetry.instrumentation.netty.v4_1.internal.server.HttpServerTracingHandler;

/** Entrypoint for instrumenting Netty HTTP servers. */
public final class NettyServerTelemetry {

  private final Instrumenter<NettyCommonRequest, HttpResponse> instrumenter;
  private final ProtocolEventHandler protocolEventHandler;

  NettyServerTelemetry(
      Instrumenter<NettyCommonRequest, HttpResponse> instrumenter,
      ProtocolEventHandler protocolEventHandler) {
    this.instrumenter = instrumenter;
    this.protocolEventHandler = protocolEventHandler;
  }

  /** Returns a new instance configured with the given {@link OpenTelemetry} instance. */
  public static NettyServerTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /** Returns a builder configured with the given {@link OpenTelemetry} instance. */
  public static NettyServerTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new NettyServerTelemetryBuilder(openTelemetry);
  }

  /**
   * Returns a handler that instruments incoming HTTP requests. Must be paired with {@link
   * #createResponseHandler()}.
   */
  public ChannelInboundHandler createRequestHandler() {
    return new HttpServerRequestTracingHandler(instrumenter);
  }

  /**
   * Returns a handler that instruments outgoing HTTP responses. Must be paired with {@link
   * #createRequestHandler()}.
   */
  public ChannelOutboundHandler createResponseHandler() {
    return createResponseHandler(HttpServerResponseBeforeCommitHandler.Noop.INSTANCE);
  }

  /**
   * @deprecated This method exposes an internal class in its API. It will be removed in the next
   *     release.
   */
  @Deprecated
  public ChannelOutboundHandler createResponseHandler(
      HttpServerResponseBeforeCommitHandler commitHandler) {
    return new HttpServerResponseTracingHandler(instrumenter, commitHandler, protocolEventHandler);
  }

  /**
   * Returns a handler that instruments incoming HTTP requests and outgoing responses in a single
   * handler.
   */
  public CombinedChannelDuplexHandler<ChannelInboundHandler, ChannelOutboundHandler>
      createCombinedHandler() {
    return createCombinedHandler(HttpServerResponseBeforeCommitHandler.Noop.INSTANCE);
  }

  /**
   * @deprecated This method exposes an internal class in its API. It will be removed in the next
   *     release.
   */
  @Deprecated
  public CombinedChannelDuplexHandler<ChannelInboundHandler, ChannelOutboundHandler>
      createCombinedHandler(HttpServerResponseBeforeCommitHandler commitHandler) {
    return new HttpServerTracingHandler(instrumenter, commitHandler, protocolEventHandler);
  }
}
