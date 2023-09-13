/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientExperimentalMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractorBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import java.util.List;
import java.util.function.Consumer;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class JettyClientInstrumenterFactory {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jetty-httpclient-9.2";

  public static Instrumenter<Request, Response> create(
      OpenTelemetry openTelemetry,
      Consumer<HttpClientAttributesExtractorBuilder<Request, Response>> extractorConfigurer,
      Consumer<HttpSpanNameExtractorBuilder<Request>> spanNameExtractorConfigurer,
      List<AttributesExtractor<? super Request, ? super Response>> additionalExtractors,
      boolean emitExperimentalHttpClientMetrics) {

    JettyClientHttpAttributesGetter httpAttributesGetter = JettyClientHttpAttributesGetter.INSTANCE;

    HttpClientAttributesExtractorBuilder<Request, Response> httpAttributesExtractorBuilder =
        HttpClientAttributesExtractor.builder(httpAttributesGetter);
    extractorConfigurer.accept(httpAttributesExtractorBuilder);

    HttpSpanNameExtractorBuilder<Request> httpSpanNameExtractorBuilder =
        HttpSpanNameExtractor.builder(httpAttributesGetter);
    spanNameExtractorConfigurer.accept(httpSpanNameExtractorBuilder);

    InstrumenterBuilder<Request, Response> builder =
        Instrumenter.<Request, Response>builder(
                openTelemetry, INSTRUMENTATION_NAME, httpSpanNameExtractorBuilder.build())
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(httpAttributesExtractorBuilder.build())
            .addAttributesExtractors(additionalExtractors)
            .addOperationMetrics(HttpClientMetrics.get());
    if (emitExperimentalHttpClientMetrics) {
      builder.addOperationMetrics(HttpClientExperimentalMetrics.get());
    }

    return builder.buildClientInstrumenter(HttpHeaderSetter.INSTANCE);
  }

  private JettyClientInstrumenterFactory() {}
}
