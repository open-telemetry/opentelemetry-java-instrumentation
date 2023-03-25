/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.httpclient.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class JdkHttpInstrumenterFactory {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.java-http-client";

  public static Instrumenter<HttpRequest, HttpResponse<?>> createInstrumenter(
      OpenTelemetry openTelemetry,
      List<String> capturedRequestHeaders,
      List<String> capturedResponseHeaders,
      List<AttributesExtractor<? super HttpRequest, ? super HttpResponse<?>>>
          additionalExtractors) {
    JdkHttpAttributesGetter httpAttributesGetter = JdkHttpAttributesGetter.INSTANCE;

    HttpClientAttributesExtractorBuilder<HttpRequest, HttpResponse<?>>
        httpAttributesExtractorBuilder =
            HttpClientAttributesExtractor.builder(
                httpAttributesGetter, new JdkHttpNetAttributesGetter());
    httpAttributesExtractorBuilder.setCapturedRequestHeaders(capturedRequestHeaders);
    httpAttributesExtractorBuilder.setCapturedResponseHeaders(capturedResponseHeaders);

    return Instrumenter.<HttpRequest, HttpResponse<?>>builder(
            openTelemetry, INSTRUMENTATION_NAME, HttpSpanNameExtractor.create(httpAttributesGetter))
        .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
        .addAttributesExtractor(httpAttributesExtractorBuilder.build())
        .addAttributesExtractors(additionalExtractors)
        .addOperationMetrics(HttpClientMetrics.get())
        .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private JdkHttpInstrumenterFactory() {}
}
