/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0.server;

import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.netty.v4.common.internal.HttpRequestAndChannel;
import io.opentelemetry.instrumentation.netty.v4.common.internal.server.NettyServerInstrumenterFactory;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;

public final class NettyServerSingletons {

  private static final Instrumenter<HttpRequestAndChannel, HttpResponse> INSTRUMENTER =
      NettyServerInstrumenterFactory.create(
          "io.opentelemetry.netty-4.0",
          CommonConfig.get().getServerRequestHeaders(),
          CommonConfig.get().getServerResponseHeaders());

  public static Instrumenter<HttpRequestAndChannel, HttpResponse> instrumenter() {
    return INSTRUMENTER;
  }

  private NettyServerSingletons() {}
}
