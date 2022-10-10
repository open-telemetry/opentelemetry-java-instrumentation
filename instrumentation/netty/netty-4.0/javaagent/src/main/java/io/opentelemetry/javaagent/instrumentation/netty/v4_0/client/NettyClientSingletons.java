/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0.client;

import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.netty.v4.common.HttpRequestAndChannel;
import io.opentelemetry.instrumentation.netty.v4.common.internal.client.NettyClientInstrumenterFactory;
import io.opentelemetry.instrumentation.netty.v4.common.internal.client.NettyConnectionInstrumenter;
import io.opentelemetry.instrumentation.netty.v4.common.internal.client.NettySslInstrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.DeprecatedConfigPropertyWarning;
import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;
import java.util.Collections;

public final class NettyClientSingletons {

  private static final boolean connectionTelemetryEnabled;
  private static final boolean sslTelemetryEnabled;

  static {
    InstrumentationConfig config = InstrumentationConfig.get();
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
            GlobalOpenTelemetry.get(),
            "io.opentelemetry.netty-4.0",
            connectionTelemetryEnabled,
            sslTelemetryEnabled,
            CommonConfig.get().getPeerServiceMapping());
    INSTRUMENTER =
        factory.createHttpInstrumenter(
            CommonConfig.get().getClientRequestHeaders(),
            CommonConfig.get().getClientResponseHeaders(),
            Collections.emptyList());
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
