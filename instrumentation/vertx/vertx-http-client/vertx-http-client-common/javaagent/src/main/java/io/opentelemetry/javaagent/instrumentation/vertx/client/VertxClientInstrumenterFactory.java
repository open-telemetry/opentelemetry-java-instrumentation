/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.client;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientExperimentalMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientPeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;

public final class VertxClientInstrumenterFactory {

  public static Instrumenter<HttpClientRequest, HttpClientResponse> create(
      String instrumentationName, AbstractVertxHttpAttributesGetter httpAttributesGetter) {

    InstrumenterBuilder<HttpClientRequest, HttpClientResponse> builder =
        Instrumenter.<HttpClientRequest, HttpClientResponse>builder(
                GlobalOpenTelemetry.get(),
                instrumentationName,
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

    return builder.buildClientInstrumenter(new HttpRequestHeaderSetter());
  }

  private VertxClientInstrumenterFactory() {}
}
