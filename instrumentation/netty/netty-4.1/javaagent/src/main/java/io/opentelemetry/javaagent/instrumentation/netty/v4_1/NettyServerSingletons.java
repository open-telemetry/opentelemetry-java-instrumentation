/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.netty.v4_1.NettyServerTelemetry;
import io.opentelemetry.instrumentation.netty.v4_1.NettyServerTelemetryBuilder;
import io.opentelemetry.instrumentation.netty.v4_1.internal.server.NettyServerInstrumenterBuilderUtil;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;

public final class NettyServerSingletons {

  static {
    NettyServerTelemetryBuilder builder = NettyServerTelemetry.builder(GlobalOpenTelemetry.get());
    NettyServerInstrumenterBuilderUtil.getBuilderExtractor()
        .apply(builder)
        .configure(AgentCommonConfig.get());
    if (AgentCommonConfig.get().shouldEmitExperimentalHttpServerTelemetry()) {
      // this logic is only used in agent
      builder.setEmitExperimentalHttpServerEvents(true);
    }
    SERVER_TELEMETRY = builder.build();
  }

  private static final NettyServerTelemetry SERVER_TELEMETRY;

  public static NettyServerTelemetry serverTelemetry() {
    return SERVER_TELEMETRY;
  }

  private NettyServerSingletons() {}
}
