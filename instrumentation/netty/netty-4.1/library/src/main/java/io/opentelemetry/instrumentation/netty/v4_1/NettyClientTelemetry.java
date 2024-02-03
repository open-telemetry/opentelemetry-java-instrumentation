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
import io.opentelemetry.instrumentation.netty.v4.common.HttpRequestAndChannel;
import io.opentelemetry.instrumentation.netty.v4.common.internal.client.NettyConnectionInstrumenter;
import io.opentelemetry.instrumentation.netty.v4.common.internal.client.NettySslInstrumenter;
import io.opentelemetry.instrumentation.netty.v4_1.internal.AttributeKeys;
import io.opentelemetry.instrumentation.netty.v4_1.internal.ProtocolEventHandler;
import io.opentelemetry.instrumentation.netty.v4_1.internal.client.HttpClientRequestTracingHandler;
import io.opentelemetry.instrumentation.netty.v4_1.internal.client.HttpClientResponseTracingHandler;
import io.opentelemetry.instrumentation.netty.v4_1.internal.client.HttpClientTracingHandler;
import java.util.function.Supplier;

/** Entrypoint for instrumenting Netty HTTP clients. */
public final class NettyClientTelemetry {

  private final Instrumenter<HttpRequestAndChannel, HttpResponse> instrumenter;
  private final Supplier<NettyConnectionInstrumenter> connectionInstrumenterSupplier;
  private final Supplier<NettySslInstrumenter> sslInstrumenterSupplier;
  private final ProtocolEventHandler protocolEventHandler;

  NettyClientTelemetry(
      Instrumenter<HttpRequestAndChannel, HttpResponse> instrumenter,
      Supplier<NettyConnectionInstrumenter> connectionInstrumenterSupplier,
      Supplier<NettySslInstrumenter> sslInstrumenterSupplier,
      ProtocolEventHandler protocolEventHandler) {
    this.instrumenter = instrumenter;
    this.connectionInstrumenterSupplier = connectionInstrumenterSupplier;
    this.sslInstrumenterSupplier = sslInstrumenterSupplier;
    this.protocolEventHandler = protocolEventHandler;
  }

  /** Returns a new {@link NettyClientTelemetry} configured with the given {@link OpenTelemetry}. */
  public static NettyClientTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /**
   * Returns a new {@link NettyClientTelemetryBuilder} configured with the given {@link
   * OpenTelemetry}.
   */
  public static NettyClientTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new NettyClientTelemetryBuilder(openTelemetry);
  }

  /**
   * /** Returns a new {@link ChannelOutboundHandlerAdapter} that generates telemetry for outgoing
   * HTTP requests. Must be paired with {@link #createResponseHandler()}.
   */
  public ChannelOutboundHandlerAdapter createRequestHandler() {
    return new HttpClientRequestTracingHandler(instrumenter);
  }

  /**
   * Returns a new {@link ChannelInboundHandlerAdapter} that generates telemetry for incoming HTTP
   * responses. Must be paired with {@link #createRequestHandler()}.
   */
  public ChannelInboundHandlerAdapter createResponseHandler() {
    return new HttpClientResponseTracingHandler(instrumenter, protocolEventHandler);
  }

  /**
   * Returns a new {@link CombinedChannelDuplexHandler} that generates telemetry for outgoing HTTP
   * requests and incoming responses in a single handler.
   */
  public CombinedChannelDuplexHandler<
          ? extends ChannelInboundHandlerAdapter, ? extends ChannelOutboundHandlerAdapter>
      createCombinedHandler() {
    return new HttpClientTracingHandler(instrumenter, protocolEventHandler);
  }

  public NettyConnectionInstrumenter getConnectionInstrumenterSupplier() {
    return connectionInstrumenterSupplier.get();
  }

  public NettySslInstrumenter getSslInstrumenterSupplier() {
    return sslInstrumenterSupplier.get();
  }

  /**
   * Propagate the {@link Context} to the {@link Channel}. This MUST be called before each HTTP
   * request executed on a {@link Channel}.
   */
  // TODO (trask) rename to setParentContext()?
  public static void setChannelContext(Channel channel, Context context) {
    channel.attr(AttributeKeys.CLIENT_PARENT_CONTEXT).set(context);
  }

  public Instrumenter<HttpRequestAndChannel, HttpResponse> getInstrumenter() {
    return instrumenter;
  }
}
