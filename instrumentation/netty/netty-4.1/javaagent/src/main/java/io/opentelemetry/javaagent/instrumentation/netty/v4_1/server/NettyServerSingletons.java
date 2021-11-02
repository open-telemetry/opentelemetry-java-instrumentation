/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1.server;

import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.AttributeKey;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.netty.common.HttpRequestAndChannel;
import io.opentelemetry.javaagent.instrumentation.netty.common.server.NettyServerInstrumenterFactory;

public final class NettyServerSingletons {

  static final AttributeKey<HttpRequestAndChannel> HTTP_REQUEST =
      AttributeKey.valueOf(NettyServerSingletons.class, "http-server-request");
  static final AttributeKey<HttpResponse> HTTP_RESPONSE =
      AttributeKey.valueOf(NettyServerSingletons.class, "http-server-response");

  private static final Instrumenter<HttpRequestAndChannel, HttpResponse> INSTRUMENTER =
      NettyServerInstrumenterFactory.create("io.opentelemetry.netty-4.1");

  public static Instrumenter<HttpRequestAndChannel, HttpResponse> instrumenter() {
    return INSTRUMENTER;
  }

  private NettyServerSingletons() {}
}
