/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientExperimentalMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientPeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.netty.v4.common.internal.client.NettyClientInstrumenterFactory;
import io.opentelemetry.instrumentation.netty.v4.common.internal.client.NettyConnectionInstrumentationFlag;
import io.opentelemetry.instrumentation.netty.v4.common.internal.client.NettyConnectionInstrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.DeprecatedConfigProperties;
import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;
import reactor.netty.http.client.HttpClientRequest;
import reactor.netty.http.client.HttpClientResponse;

public final class ReactorNettySingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.reactor-netty-1.0";

  private static final boolean connectionTelemetryEnabled;

  static {
    InstrumentationConfig config = InstrumentationConfig.get();
    connectionTelemetryEnabled =
        DeprecatedConfigProperties.getBoolean(
            config,
            "otel.instrumentation.reactor-netty.always-create-connect-span",
            "otel.instrumentation.reactor-netty.connection-telemetry.enabled",
            false);
  }

  private static final Instrumenter<HttpClientRequest, HttpClientResponse> INSTRUMENTER;
  private static final NettyConnectionInstrumenter CONNECTION_INSTRUMENTER;

  static {
    ReactorNettyHttpClientAttributesGetter httpAttributesGetter =
        new ReactorNettyHttpClientAttributesGetter();

    InstrumenterBuilder<HttpClientRequest, HttpClientResponse> builder =
        Instrumenter.<HttpClientRequest, HttpClientResponse>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                HttpSpanNameExtractor.builder(httpAttributesGetter)
                    .setKnownMethods(CommonConfig.get().getKnownHttpRequestMethods())
                    .build())
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(
                HttpClientAttributesExtractor.builder(httpAttributesGetter)
                    .setCapturedRequestHeaders(CommonConfig.get().getClientRequestHeaders())
                    .setCapturedResponseHeaders(CommonConfig.get().getClientResponseHeaders())
                    .setKnownMethods(CommonConfig.get().getKnownHttpRequestMethods())
                    .build())
            .addAttributesExtractor(
                HttpClientPeerServiceAttributesExtractor.create(
                    httpAttributesGetter, CommonConfig.get().getPeerServiceResolver()))
            .addOperationMetrics(HttpClientMetrics.get());
    if (CommonConfig.get().shouldEmitExperimentalHttpClientMetrics()) {
      builder.addOperationMetrics(HttpClientExperimentalMetrics.get());
    }
    INSTRUMENTER = builder.buildClientInstrumenter(HttpClientRequestHeadersSetter.INSTANCE);

    NettyClientInstrumenterFactory instrumenterFactory =
        new NettyClientInstrumenterFactory(
            GlobalOpenTelemetry.get(),
            INSTRUMENTATION_NAME,
            connectionTelemetryEnabled
                ? NettyConnectionInstrumentationFlag.ENABLED
                : NettyConnectionInstrumentationFlag.DISABLED,
            NettyConnectionInstrumentationFlag.DISABLED,
            CommonConfig.get().getPeerServiceResolver(),
            CommonConfig.get().shouldEmitExperimentalHttpClientMetrics());
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
