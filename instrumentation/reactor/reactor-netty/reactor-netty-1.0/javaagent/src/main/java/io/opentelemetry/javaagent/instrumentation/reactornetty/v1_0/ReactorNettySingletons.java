/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpClientExperimentalMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpClientPeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpExperimentalAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.netty.v4.common.internal.client.NettyClientInstrumenterFactory;
import io.opentelemetry.instrumentation.netty.v4.common.internal.client.NettyConnectionInstrumentationFlag;
import io.opentelemetry.instrumentation.netty.v4.common.internal.client.NettyConnectionInstrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;
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
    if (CommonConfig.get().shouldEmitExperimentalHttpClientTelemetry()) {
      builder
          .addAttributesExtractor(HttpExperimentalAttributesExtractor.create(httpAttributesGetter))
          .addOperationMetrics(HttpClientExperimentalMetrics.get());
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
