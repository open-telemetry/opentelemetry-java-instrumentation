/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.googlehttpclient;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
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
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;

public class GoogleHttpClientSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.google-http-client-1.19";

  private static final Instrumenter<HttpRequest, HttpResponse> INSTRUMENTER;

  static {
    GoogleHttpClientHttpAttributesGetter httpAttributesGetter =
        new GoogleHttpClientHttpAttributesGetter();

    InstrumenterBuilder<HttpRequest, HttpResponse> builder =
        Instrumenter.<HttpRequest, HttpResponse>builder(
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
    INSTRUMENTER = builder.buildClientInstrumenter(HttpHeaderSetter.INSTANCE);
  }

  public static Instrumenter<HttpRequest, HttpResponse> instrumenter() {
    return INSTRUMENTER;
  }

  private GoogleHttpClientSingletons() {}
}
