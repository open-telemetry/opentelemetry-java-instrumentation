/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.netty.common.client.NettyClientInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.netty.common.client.NettyConnectionInstrumenter;
import reactor.netty.http.client.HttpClientConfig;
import reactor.netty.http.client.HttpClientResponse;

public final class ReactorNettySingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.reactor-netty-1.0";

  private static final boolean connectionTelemetryEnabled;

  static {
    Config config = Config.get();
    boolean alwaysCreateConnectSpan =
        config.getBoolean("otel.instrumentation.reactor-netty.always-create-connect-span", false);
    connectionTelemetryEnabled =
        config.getBoolean(
            "otel.instrumentation.reactor-netty.connection-telemetry.enabled",
            alwaysCreateConnectSpan);
  }

  private static final Instrumenter<HttpClientConfig, HttpClientResponse> INSTRUMENTER;
  private static final NettyConnectionInstrumenter CONNECTION_INSTRUMENTER;

  static {
    ReactorNettyHttpClientAttributesGetter httpAttributesGetter =
        new ReactorNettyHttpClientAttributesGetter();
    ReactorNettyNetClientAttributesGetter netAttributesGetter =
        new ReactorNettyNetClientAttributesGetter();

    INSTRUMENTER =
        Instrumenter.<HttpClientConfig, HttpClientResponse>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                HttpSpanNameExtractor.create(httpAttributesGetter))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(HttpClientAttributesExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(NetClientAttributesExtractor.create(netAttributesGetter))
            .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesGetter))
            .addRequestMetrics(HttpClientMetrics.get())
            // headers are injected in ResponseReceiverInstrumenter
            .newInstrumenter(SpanKindExtractor.alwaysClient());

    NettyClientInstrumenterFactory instrumenterFactory =
        new NettyClientInstrumenterFactory(INSTRUMENTATION_NAME, connectionTelemetryEnabled, false);
    CONNECTION_INSTRUMENTER = instrumenterFactory.createConnectionInstrumenter();
  }

  public static Instrumenter<HttpClientConfig, HttpClientResponse> instrumenter() {
    return INSTRUMENTER;
  }

  public static NettyConnectionInstrumenter connectionInstrumenter() {
    return CONNECTION_INSTRUMENTER;
  }

  private ReactorNettySingletons() {}
}
