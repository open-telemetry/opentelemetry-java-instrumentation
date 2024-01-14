/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.httpclient.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpClientExperimentalMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpExperimentalAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractorBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanStatusExtractor;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.function.Consumer;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class JavaHttpClientInstrumenterFactory {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.java-http-client";

  public static Instrumenter<HttpRequest, HttpResponse<?>> createInstrumenter(
      OpenTelemetry openTelemetry,
      Consumer<HttpClientAttributesExtractorBuilder<HttpRequest, HttpResponse<?>>>
          extractorConfigurer,
      Consumer<HttpSpanNameExtractorBuilder<HttpRequest>> spanNameExtractorConfigurer,
      List<AttributesExtractor<? super HttpRequest, ? super HttpResponse<?>>> additionalExtractors,
      boolean emitExperimentalHttpClientMetrics) {

    JavaHttpClientAttributesGetter httpAttributesGetter = JavaHttpClientAttributesGetter.INSTANCE;

    HttpClientAttributesExtractorBuilder<HttpRequest, HttpResponse<?>>
        httpAttributesExtractorBuilder =
            HttpClientAttributesExtractor.builder(httpAttributesGetter);
    extractorConfigurer.accept(httpAttributesExtractorBuilder);

    HttpSpanNameExtractorBuilder<HttpRequest> httpSpanNameExtractorBuilder =
        HttpSpanNameExtractor.builder(httpAttributesGetter);
    spanNameExtractorConfigurer.accept(httpSpanNameExtractorBuilder);

    InstrumenterBuilder<HttpRequest, HttpResponse<?>> builder =
        Instrumenter.<HttpRequest, HttpResponse<?>>builder(
                openTelemetry, INSTRUMENTATION_NAME, httpSpanNameExtractorBuilder.build())
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(httpAttributesExtractorBuilder.build())
            .addAttributesExtractors(additionalExtractors)
            .addOperationMetrics(HttpClientMetrics.get());
    if (emitExperimentalHttpClientMetrics) {
      builder
          .addAttributesExtractor(HttpExperimentalAttributesExtractor.create(httpAttributesGetter))
          .addOperationMetrics(HttpClientExperimentalMetrics.get());
    }
    return builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private JavaHttpClientInstrumenterFactory() {}
}
