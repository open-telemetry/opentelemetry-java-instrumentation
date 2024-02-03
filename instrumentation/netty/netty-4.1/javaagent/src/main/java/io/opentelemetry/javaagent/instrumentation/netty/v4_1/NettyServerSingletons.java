/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1;

import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.netty.v4.common.HttpRequestAndChannel;
import io.opentelemetry.instrumentation.netty.v4.common.internal.server.NettyServerInstrumenterFactory;
import io.opentelemetry.instrumentation.netty.v4_1.NettyServerTelemetry;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;

public final class NettyServerSingletons {

  static {
    boolean experimentalEvents =
        InstrumentationConfig.get()
            .getBoolean("otel.instrumentation.netty.experimental-events", false);

    SERVER_TELEMETRY =
        NettyServerTelemetry.builder(GlobalOpenTelemetry.get())
            .setCaptureExperimentalEvents(experimentalEvents)
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

  private static final Instrumenter<HttpRequestAndChannel, HttpResponse> INSTRUMENTER =
      NettyServerInstrumenterFactory.create(
          GlobalOpenTelemetry.get(),
          "io.opentelemetry.netty-4.1",
          builder ->
              builder
                  .setCapturedRequestHeaders(CommonConfig.get().getServerRequestHeaders())
                  .setCapturedResponseHeaders(CommonConfig.get().getServerResponseHeaders())
                  .setKnownMethods(CommonConfig.get().getKnownHttpRequestMethods()),
          builder -> builder.setKnownMethods(CommonConfig.get().getKnownHttpRequestMethods()),
          builder -> builder.setKnownMethods(CommonConfig.get().getKnownHttpRequestMethods()),
          CommonConfig.get().shouldEmitExperimentalHttpServerTelemetry());

  public static Instrumenter<HttpRequestAndChannel, HttpResponse> instrumenter() {
    return INSTRUMENTER;
  }

  private NettyServerSingletons() {}
}
