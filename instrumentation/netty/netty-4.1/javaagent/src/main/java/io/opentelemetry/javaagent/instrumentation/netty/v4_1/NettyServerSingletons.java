/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1;

import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.CombinedChannelDuplexHandler;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.NettyCommonRequest;
import io.opentelemetry.instrumentation.netty.v4_1.NettyServerTelemetry;
import io.opentelemetry.instrumentation.netty.v4_1.NettyServerTelemetryBuilder;
import io.opentelemetry.instrumentation.netty.v4_1.internal.ProtocolEventHandler;
import io.opentelemetry.instrumentation.netty.v4_1.internal.server.HttpServerRequestTracingHandler;
import io.opentelemetry.instrumentation.netty.v4_1.internal.server.HttpServerResponseTracingHandler;
import io.opentelemetry.instrumentation.netty.v4_1.internal.server.HttpServerTracingHandler;
import io.opentelemetry.instrumentation.netty.v4_1.internal.server.NettyServerInstrumenterBuilderUtil;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;

public final class NettyServerSingletons {

  private static final Instrumenter<NettyCommonRequest, HttpResponse> instrumenter;
  private static final ProtocolEventHandler protocolEventHandler;

  static {
    NettyServerTelemetryBuilder builder = NettyServerTelemetry.builder(GlobalOpenTelemetry.get());
    NettyServerInstrumenterBuilderUtil.getBuilderExtractor()
        .apply(builder)
        .configure(AgentCommonConfig.get());
    boolean emitExperimental = AgentCommonConfig.get().shouldEmitExperimentalHttpServerTelemetry();
    if (emitExperimental) {
      NettyServerInstrumenterBuilderUtil.getBuilderExtractor()
          .apply(builder)
          .setEmitExperimentalHttpServerTelemetry(true);
    }
    instrumenter = NettyServerInstrumenterBuilderUtil.getBuilderExtractor().apply(builder).build();
    protocolEventHandler =
        emitExperimental
            ? ProtocolEventHandler.Enabled.INSTANCE
            : ProtocolEventHandler.Noop.INSTANCE;
  }

  public static ChannelInboundHandler createRequestHandler() {
    return new HttpServerRequestTracingHandler(instrumenter);
  }

  public static ChannelOutboundHandler createResponseHandler() {
    return new HttpServerResponseTracingHandler(
        instrumenter, NettyHttpServerResponseBeforeCommitHandler.INSTANCE, protocolEventHandler);
  }

  public static CombinedChannelDuplexHandler<ChannelInboundHandler, ChannelOutboundHandler>
      createCombinedHandler() {
    return new HttpServerTracingHandler(
        instrumenter, NettyHttpServerResponseBeforeCommitHandler.INSTANCE, protocolEventHandler);
  }

  private NettyServerSingletons() {}
}
