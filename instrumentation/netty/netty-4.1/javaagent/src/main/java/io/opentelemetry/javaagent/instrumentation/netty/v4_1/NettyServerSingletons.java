/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.netty.v4_1.NettyServerTelemetry;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;

public final class NettyServerSingletons {

  static {
    SERVER_TELEMETRY =
        NettyServerTelemetry.builder(GlobalOpenTelemetry.get())
            .setEmitExperimentalHttpServerEvents(
                CommonConfig.get().shouldEmitExperimentalHttpServerTelemetry())
            .setEmitExperimentalHttpServerMetrics(
                CommonConfig.get().shouldEmitExperimentalHttpServerTelemetry())
            .setKnownMethods(CommonConfig.get().getKnownHttpRequestMethods())
            .setCapturedRequestHeaders(CommonConfig.get().getServerRequestHeaders())
            .setCapturedResponseHeaders(CommonConfig.get().getServerResponseHeaders())
            .build();
  }

  private static final NettyServerTelemetry SERVER_TELEMETRY;

  public static NettyServerTelemetry serverTelemetry() {
    return SERVER_TELEMETRY;
  }

  private NettyServerSingletons() {}
}
