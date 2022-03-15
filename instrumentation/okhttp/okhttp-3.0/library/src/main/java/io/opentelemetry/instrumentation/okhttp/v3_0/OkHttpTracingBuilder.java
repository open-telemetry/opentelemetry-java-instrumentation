/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0;

import static io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor.alwaysClient;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.opentelemetry.instrumentation.okhttp.v3_0.internal.OkHttpNetAttributesGetter;
import java.util.ArrayList;
import java.util.List;
import okhttp3.Request;
import okhttp3.Response;

/** A builder of {@link OkHttpTracing}. */
public final class OkHttpTracingBuilder {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.okhttp-3.0";

  private final OpenTelemetry openTelemetry;
  private final List<AttributesExtractor<Request, Response>> additionalExtractors =
      new ArrayList<>();
  private final HttpClientAttributesExtractorBuilder<Request, Response>
      httpAttributesExtractorBuilder =
          HttpClientAttributesExtractor.builder(OkHttpAttributesGetter.INSTANCE);

  OkHttpTracingBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items.
   */
  public OkHttpTracingBuilder addAttributesExtractor(
      AttributesExtractor<Request, Response> attributesExtractor) {
    additionalExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Configures the HTTP request headers that will be captured as span attributes.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  public OkHttpTracingBuilder setCapturedRequestHeaders(List<String> requestHeaders) {
    httpAttributesExtractorBuilder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures the HTTP response headers that will be captured as span attributes.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  public OkHttpTracingBuilder setCapturedResponseHeaders(List<String> responseHeaders) {
    httpAttributesExtractorBuilder.setCapturedResponseHeaders(responseHeaders);
    return this;
  }

  /** Returns a new {@link OkHttpTracing} with the settings of this {@link OkHttpTracingBuilder}. */
  public OkHttpTracing build() {
    OkHttpAttributesGetter httpAttributesGetter = OkHttpAttributesGetter.INSTANCE;
    OkHttpNetAttributesGetter attributesGetter = new OkHttpNetAttributesGetter();

    Instrumenter<Request, Response> instrumenter =
        Instrumenter.<Request, Response>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                HttpSpanNameExtractor.create(httpAttributesGetter))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(httpAttributesExtractorBuilder.build())
            .addAttributesExtractor(NetClientAttributesExtractor.create(attributesGetter))
            .addAttributesExtractors(additionalExtractors)
            .addRequestMetrics(HttpClientMetrics.get())
            .newInstrumenter(alwaysClient());
    return new OkHttpTracing(instrumenter, openTelemetry.getPropagators());
  }
}
