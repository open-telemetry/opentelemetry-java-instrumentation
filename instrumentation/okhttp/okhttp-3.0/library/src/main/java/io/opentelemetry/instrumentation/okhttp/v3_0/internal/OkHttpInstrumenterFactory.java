/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0.internal;

import static io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor.alwaysClient;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import java.util.List;
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
      List<String> capturedRequestHeaders,
      List<String> capturedResponseHeaders,
      List<AttributesExtractor<Request, Response>> additionalAttributesExtractors) {

    OkHttpAttributesGetter httpAttributesGetter = OkHttpAttributesGetter.INSTANCE;
    OkHttpNetAttributesGetter netAttributesGetter = OkHttpNetAttributesGetter.INSTANCE;

    return Instrumenter.<Request, Response>builder(
            openTelemetry, INSTRUMENTATION_NAME, HttpSpanNameExtractor.create(httpAttributesGetter))
        .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
        .addAttributesExtractor(
            HttpClientAttributesExtractor.builder(httpAttributesGetter, netAttributesGetter)
                .setCapturedRequestHeaders(capturedRequestHeaders)
                .setCapturedResponseHeaders(capturedResponseHeaders)
                .build())
        .addAttributesExtractors(additionalAttributesExtractors)
        .addOperationMetrics(HttpClientMetrics.get())
        .buildInstrumenter(alwaysClient());
  }

  private OkHttpInstrumenterFactory() {}
}
