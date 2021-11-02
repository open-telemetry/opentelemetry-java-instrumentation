/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1.client;

import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.AttributeKey;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.netty.common.HttpRequestAndChannel;
import io.opentelemetry.javaagent.instrumentation.netty.common.client.NettyClientInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.netty.common.client.NettyConnectInstrumenter;

public final class NettyClientSingletons {

  public static final AttributeKey<HttpRequestAndChannel> HTTP_REQUEST =
      AttributeKey.valueOf(NettyClientSingletons.class, "http-client-request");
  static final AttributeKey<HttpResponse> HTTP_RESPONSE =
      AttributeKey.valueOf(NettyClientSingletons.class, "http-client-response");

  private static final boolean alwaysCreateConnectSpan =
      Config.get().getBoolean("otel.instrumentation.netty.always-create-connect-span", false);

  private static final Instrumenter<HttpRequestAndChannel, HttpResponse> INSTRUMENTER;
  private static final NettyConnectInstrumenter CONNECT_INSTRUMENTER;

  static {
    NettyClientInstrumenterFactory factory =
        new NettyClientInstrumenterFactory("io.opentelemetry.netty-4.1", alwaysCreateConnectSpan);
    INSTRUMENTER = factory.createHttpInstrumenter();
    CONNECT_INSTRUMENTER = factory.createConnectInstrumenter();
  }

  public static Instrumenter<HttpRequestAndChannel, HttpResponse> instrumenter() {
    return INSTRUMENTER;
  }

  public static NettyConnectInstrumenter connectInstrumenter() {
    return CONNECT_INSTRUMENTER;
  }

  private NettyClientSingletons() {}
}
