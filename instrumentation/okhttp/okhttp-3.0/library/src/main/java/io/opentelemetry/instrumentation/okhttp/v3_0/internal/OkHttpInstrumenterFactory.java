/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0.internal;

import static io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor.alwaysClient;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpClientExperimentalMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpExperimentalAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractorBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanStatusExtractor;
import java.util.List;
import java.util.function.Consumer;
import okhttp3.Request;
import okhttp3.Response;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class OkHttpInstrumenterFactory {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.okhttp-3.0";

  public static Instrumenter<Request, Response> create(
      OpenTelemetry openTelemetry,
      Consumer<HttpClientAttributesExtractorBuilder<Request, Response>> extractorConfigurer,
      Consumer<HttpSpanNameExtractorBuilder<Request>> spanNameExtractorConfigurer,
      List<AttributesExtractor<Request, Response>> additionalAttributesExtractors,
      boolean emitExperimentalHttpClientMetrics) {

    OkHttpAttributesGetter httpAttributesGetter = OkHttpAttributesGetter.INSTANCE;

    HttpClientAttributesExtractorBuilder<Request, Response> extractorBuilder =
        HttpClientAttributesExtractor.builder(httpAttributesGetter);
    extractorConfigurer.accept(extractorBuilder);

    HttpSpanNameExtractorBuilder<Request> httpSpanNameExtractorBuilder =
        HttpSpanNameExtractor.builder(httpAttributesGetter);
    spanNameExtractorConfigurer.accept(httpSpanNameExtractorBuilder);

    InstrumenterBuilder<Request, Response> builder =
        Instrumenter.<Request, Response>builder(
                openTelemetry, INSTRUMENTATION_NAME, httpSpanNameExtractorBuilder.build())
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(extractorBuilder.build())
            .addAttributesExtractors(additionalAttributesExtractors)
            .addOperationMetrics(HttpClientMetrics.get());
    if (emitExperimentalHttpClientMetrics) {
      builder
          .addAttributesExtractor(HttpExperimentalAttributesExtractor.create(httpAttributesGetter))
          .addOperationMetrics(HttpClientExperimentalMetrics.get());
    }

    return builder.buildInstrumenter(alwaysClient());
  }

  private OkHttpInstrumenterFactory() {}
}
