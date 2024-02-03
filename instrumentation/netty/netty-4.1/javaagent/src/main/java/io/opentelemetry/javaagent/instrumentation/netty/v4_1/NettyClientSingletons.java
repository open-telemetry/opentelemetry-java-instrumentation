/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1;

import static io.opentelemetry.instrumentation.netty.v4.common.internal.client.NettyConnectionInstrumentationFlag.enabledOrErrorOnly;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.netty.v4.common.internal.client.NettyConnectionInstrumenter;
import io.opentelemetry.instrumentation.netty.v4.common.internal.client.NettySslInstrumenter;
import io.opentelemetry.instrumentation.netty.v4_1.NettyClientTelemetry;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;

public final class NettyClientSingletons {

  private static final boolean connectionTelemetryEnabled =
      InstrumentationConfig.get()
          .getBoolean("otel.instrumentation.netty.connection-telemetry.enabled", false);
  private static final boolean sslTelemetryEnabled =
      InstrumentationConfig.get()
          .getBoolean("otel.instrumentation.netty.ssl-telemetry.enabled", false);

  static {
    CLIENT_TELEMETRY =
        NettyClientTelemetry.builder(GlobalOpenTelemetry.get())
            .setConnectionTelemetryState(enabledOrErrorOnly(connectionTelemetryEnabled))
            .setSslTelemetryState(enabledOrErrorOnly(sslTelemetryEnabled))
            .setPeerServiceResolver(CommonConfig.get().getPeerServiceResolver())
            .setKnownMethods(CommonConfig.get().getKnownHttpRequestMethods())
            .setCapturedRequestHeaders(CommonConfig.get().getClientRequestHeaders())
            .setCapturedResponseHeaders(CommonConfig.get().getClientResponseHeaders())
            .setEmitExperimentalHttpClientMetrics(
                CommonConfig.get().shouldEmitExperimentalHttpClientTelemetry())
            .setEmitExperimentalHttpClientEvents(
                CommonConfig.get().shouldEmitExperimentalHttpServerTelemetry())
            .build();
  }

  private static final NettyClientTelemetry CLIENT_TELEMETRY;
  private static final NettyConnectionInstrumenter CONNECTION_INSTRUMENTER;
  private static final NettySslInstrumenter SSL_INSTRUMENTER;

  static {
    CONNECTION_INSTRUMENTER = CLIENT_TELEMETRY.getConnectionInstrumenterSupplier();
    SSL_INSTRUMENTER = CLIENT_TELEMETRY.getSslInstrumenterSupplier();
  }

  public static NettyClientTelemetry clientTelemetry() {
    return CLIENT_TELEMETRY;
  }

  public static NettyConnectionInstrumenter connectionInstrumenter() {
    return CONNECTION_INSTRUMENTER;
  }

  public static NettySslInstrumenter sslInstrumenter() {
    return SSL_INSTRUMENTER;
  }

  private NettyClientSingletons() {}
}
