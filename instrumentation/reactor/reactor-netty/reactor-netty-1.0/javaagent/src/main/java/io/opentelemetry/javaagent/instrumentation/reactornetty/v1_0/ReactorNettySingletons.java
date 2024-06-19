/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.netty.v4.common.internal.client.NettyClientInstrumenterFactory;
import io.opentelemetry.instrumentation.netty.v4.common.internal.client.NettyConnectionInstrumentationFlag;
import io.opentelemetry.instrumentation.netty.v4.common.internal.client.NettyConnectionInstrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpClientInstrumenterBuilder;
import reactor.netty.http.client.HttpClientRequest;
import reactor.netty.http.client.HttpClientResponse;

public final class ReactorNettySingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.reactor-netty-1.0";

  private static final boolean connectionTelemetryEnabled =
      InstrumentationConfig.get()
          .getBoolean("otel.instrumentation.reactor-netty.connection-telemetry.enabled", false);

  private static final Instrumenter<HttpClientRequest, HttpClientResponse> INSTRUMENTER;
  private static final NettyConnectionInstrumenter CONNECTION_INSTRUMENTER;

  static {
    INSTRUMENTER =
        JavaagentHttpClientInstrumenterBuilder.create(
                INSTRUMENTATION_NAME, new ReactorNettyHttpClientAttributesGetter())
            .buildClientInstrumenter(HttpClientRequestHeadersSetter.INSTANCE);

    NettyClientInstrumenterFactory instrumenterFactory =
        new NettyClientInstrumenterFactory(
            GlobalOpenTelemetry.get(),
            INSTRUMENTATION_NAME,
            connectionTelemetryEnabled
                ? NettyConnectionInstrumentationFlag.ENABLED
                : NettyConnectionInstrumentationFlag.DISABLED,
            NettyConnectionInstrumentationFlag.DISABLED,
            CommonConfig.get().getPeerServiceResolver(),
            CommonConfig.get().shouldEmitExperimentalHttpClientTelemetry());
    CONNECTION_INSTRUMENTER = instrumenterFactory.createConnectionInstrumenter();
  }

  public static Instrumenter<HttpClientRequest, HttpClientResponse> instrumenter() {
    return INSTRUMENTER;
  }

  public static NettyConnectionInstrumenter connectionInstrumenter() {
    return CONNECTION_INSTRUMENTER;
  }

  private ReactorNettySingletons() {}
}
