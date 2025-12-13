/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.netty.v4_1.NettyServerTelemetry;
import io.opentelemetry.instrumentation.netty.v4_1.NettyServerTelemetryBuilder;
import io.opentelemetry.instrumentation.netty.v4_1.internal.server.NettyServerInstrumenterBuilderUtil;

public final class NettyServerSingletons {

  static {
    NettyServerTelemetryBuilder builder = NettyServerTelemetry.builder(GlobalOpenTelemetry.get());
    NettyServerInstrumenterBuilderUtil.getBuilderExtractor()
        .apply(builder)
        .configure(GlobalOpenTelemetry.get());
    if (DeclarativeConfigUtil.getBoolean(
            GlobalOpenTelemetry.get(), "general", "http", "server", "emit_experimental_telemetry")
        .orElse(false)) {
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
