/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.client;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import javax.annotation.Nullable;

public final class VertxClientInstrumenterFactory {

  public static Instrumenter<HttpClientRequest, HttpClientResponse> create(
      String instrumentationName,
      AbstractVertxHttpAttributesGetter httpAttributesGetter,
      @Nullable
          NetClientAttributesGetter<HttpClientRequest, HttpClientResponse> netAttributesGetter) {

    InstrumenterBuilder<HttpClientRequest, HttpClientResponse> builder =
        Instrumenter.<HttpClientRequest, HttpClientResponse>builder(
                GlobalOpenTelemetry.get(),
                instrumentationName,
                HttpSpanNameExtractor.create(httpAttributesGetter))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(HttpClientAttributesExtractor.create(httpAttributesGetter))
            .addRequestMetrics(HttpClientMetrics.get());

    if (netAttributesGetter != null) {
      NetClientAttributesExtractor<HttpClientRequest, HttpClientResponse> netAttributesExtractor =
          NetClientAttributesExtractor.create(netAttributesGetter);
      builder
          .addAttributesExtractor(netAttributesExtractor)
          .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesGetter));
    }

    return builder.newClientInstrumenter(new HttpRequestHeaderSetter());
  }

  private VertxClientInstrumenterFactory() {}
}
