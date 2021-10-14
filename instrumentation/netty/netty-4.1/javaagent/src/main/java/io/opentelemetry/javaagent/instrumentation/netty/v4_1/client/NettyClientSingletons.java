/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1.client;

import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.AttributeKey;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.netty.common.HttpRequestAndChannel;
import io.opentelemetry.javaagent.instrumentation.netty.common.client.NettyClientInstrumenterFactory;

public final class NettyClientSingletons {

  static final AttributeKey<HttpRequestAndChannel> HTTP_REQUEST =
      AttributeKey.valueOf(NettyClientSingletons.class, "http-client-request");
  static final AttributeKey<HttpResponse> HTTP_RESPONSE =
      AttributeKey.valueOf(NettyClientSingletons.class, "http-client-response");

  private static final Instrumenter<HttpRequestAndChannel, HttpResponse> INSTRUMENTER;

  static {
    NettyClientInstrumenterFactory factory =
        new NettyClientInstrumenterFactory("io.opentelemetry.netty-4.1");
    INSTRUMENTER = factory.createHttpInstrumenter();
  }

  public static Instrumenter<HttpRequestAndChannel, HttpResponse> instrumenter() {
    return INSTRUMENTER;
  }

  private NettyClientSingletons() {}
}
