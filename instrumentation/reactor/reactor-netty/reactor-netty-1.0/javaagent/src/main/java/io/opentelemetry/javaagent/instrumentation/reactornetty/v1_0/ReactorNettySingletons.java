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

  private static final boolean alwaysCreateConnectSpan =
      Config.get()
          .getBoolean("otel.instrumentation.reactor-netty.always-create-connect-span", false);

  private static final Instrumenter<HttpClientConfig, HttpClientResponse> INSTRUMENTER;
  private static final NettyConnectionInstrumenter CONNECTION_INSTRUMENTER;

  static {
    ReactorNettyHttpClientAttributesExtractor httpAttributesExtractor =
        new ReactorNettyHttpClientAttributesExtractor();
    ReactorNettyNetClientAttributesGetter netAttributesAdapter =
        new ReactorNettyNetClientAttributesGetter();

    INSTRUMENTER =
        Instrumenter.<HttpClientConfig, HttpClientResponse>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                HttpSpanNameExtractor.create(httpAttributesExtractor))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesExtractor))
            .addAttributesExtractor(httpAttributesExtractor)
            .addAttributesExtractor(NetClientAttributesExtractor.create(netAttributesAdapter))
            .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesAdapter))
            .addRequestMetrics(HttpClientMetrics.get())
            // headers are injected in ResponseReceiverInstrumenter
            .newInstrumenter(SpanKindExtractor.alwaysClient());

    NettyClientInstrumenterFactory instrumenterFactory =
        new NettyClientInstrumenterFactory(INSTRUMENTATION_NAME, alwaysCreateConnectSpan, false);
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
