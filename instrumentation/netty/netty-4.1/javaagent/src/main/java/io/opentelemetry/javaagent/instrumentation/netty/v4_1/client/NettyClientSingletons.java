/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1.client;

import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.AttributeKey;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.DeprecatedConfigPropertyWarning;
import io.opentelemetry.javaagent.instrumentation.netty.common.HttpRequestAndChannel;
import io.opentelemetry.javaagent.instrumentation.netty.common.client.NettyClientInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.netty.common.client.NettyConnectionInstrumenter;
import io.opentelemetry.javaagent.instrumentation.netty.common.client.NettySslInstrumenter;

public final class NettyClientSingletons {

  public static final AttributeKey<HttpRequestAndChannel> HTTP_REQUEST =
      AttributeKey.valueOf(NettyClientSingletons.class, "http-client-request");
  static final AttributeKey<HttpResponse> HTTP_RESPONSE =
      AttributeKey.valueOf(NettyClientSingletons.class, "http-client-response");

  private static final boolean connectionTelemetryEnabled;
  private static final boolean sslTelemetryEnabled;

  static {
    Config config = Config.get();
    DeprecatedConfigPropertyWarning.warnIfUsed(
        config,
        "otel.instrumentation.netty.always-create-connect-span",
        "otel.instrumentation.netty.connection-telemetry.enabled");
    boolean alwaysCreateConnectSpan =
        config.getBoolean("otel.instrumentation.netty.always-create-connect-span", false);
    connectionTelemetryEnabled =
        config.getBoolean(
            "otel.instrumentation.netty.connection-telemetry.enabled", alwaysCreateConnectSpan);
    sslTelemetryEnabled =
        config.getBoolean("otel.instrumentation.netty.ssl-telemetry.enabled", false);
  }

  private static final Instrumenter<HttpRequestAndChannel, HttpResponse> INSTRUMENTER;
  private static final NettyConnectionInstrumenter CONNECTION_INSTRUMENTER;
  private static final NettySslInstrumenter SSL_INSTRUMENTER;

  static {
    NettyClientInstrumenterFactory factory =
        new NettyClientInstrumenterFactory(
            "io.opentelemetry.netty-4.1", connectionTelemetryEnabled, sslTelemetryEnabled);
    INSTRUMENTER = factory.createHttpInstrumenter();
    CONNECTION_INSTRUMENTER = factory.createConnectionInstrumenter();
    SSL_INSTRUMENTER = factory.createSslInstrumenter();
  }

  public static Instrumenter<HttpRequestAndChannel, HttpResponse> instrumenter() {
    return INSTRUMENTER;
  }

  public static NettyConnectionInstrumenter connectionInstrumenter() {
    return CONNECTION_INSTRUMENTER;
  }

  public static NettySslInstrumenter sslInstrumenter() {
    return SSL_INSTRUMENTER;
  }

  private NettyClientSingletons() {}
}
