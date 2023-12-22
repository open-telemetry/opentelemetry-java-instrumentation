/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0.server;

import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.netty.v4.common.HttpRequestAndChannel;
import io.opentelemetry.instrumentation.netty.v4.common.internal.server.NettyServerInstrumenterFactory;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;

public final class NettyServerSingletons {

  private static final Instrumenter<HttpRequestAndChannel, HttpResponse> INSTRUMENTER =
      NettyServerInstrumenterFactory.create(
          GlobalOpenTelemetry.get(),
          "io.opentelemetry.netty-4.0",
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
