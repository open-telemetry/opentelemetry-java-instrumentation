/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0.client;

import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.netty.common.HttpRequestAndChannel;
import io.opentelemetry.javaagent.instrumentation.netty.common.client.NettyClientInstrumenterFactory;

public final class NettyClientSingletons {

  private static final Instrumenter<HttpRequestAndChannel, HttpResponse> INSTRUMENTER;

  static {
    NettyClientInstrumenterFactory factory =
        new NettyClientInstrumenterFactory("io.opentelemetry.netty-4.0");
    INSTRUMENTER = factory.createHttpInstrumenter();
  }

  public static Instrumenter<HttpRequestAndChannel, HttpResponse> instrumenter() {
    return INSTRUMENTER;
  }

  private NettyClientSingletons() {}
}
