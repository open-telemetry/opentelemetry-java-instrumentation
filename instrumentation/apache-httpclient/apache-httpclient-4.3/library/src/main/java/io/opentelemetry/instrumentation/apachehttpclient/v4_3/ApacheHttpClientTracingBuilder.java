/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachehttpclient.v4_3;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.HttpResponse;

/** A builder for {@link ApacheHttpClientTracing}. */
public final class ApacheHttpClientTracingBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.apache-httpclient-4.3";

  private final OpenTelemetry openTelemetry;

  private final List<AttributesExtractor<? super ApacheHttpClientRequest, ? super HttpResponse>>
      additionalExtractors = new ArrayList<>();
  private final HttpClientAttributesExtractorBuilder<ApacheHttpClientRequest, HttpResponse>
      httpAttributesExtractorBuilder =
          HttpClientAttributesExtractor.builder(ApacheHttpClientHttpAttributesGetter.INSTANCE);

  ApacheHttpClientTracingBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items. The {@link AttributesExtractor} will be executed after all default extractors.
   */
  public ApacheHttpClientTracingBuilder addAttributeExtractor(
      AttributesExtractor<? super ApacheHttpClientRequest, ? super HttpResponse>
          attributesExtractor) {
    additionalExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Configure the instrumentation to capture chosen HTTP request and response headers as span
   * attributes.
   *
   * @param capturedHttpHeaders An instance of {@link
   *     io.opentelemetry.instrumentation.api.instrumenter.http.CapturedHttpHeaders} containing the
   *     configured HTTP request and response names.
   * @deprecated Use {@link #setCapturedRequestHeaders(List)} and {@link
   *     #setCapturedResponseHeaders(List)} instead.
   */
  @Deprecated
  public ApacheHttpClientTracingBuilder captureHttpHeaders(
      io.opentelemetry.instrumentation.api.instrumenter.http.CapturedHttpHeaders
          capturedHttpHeaders) {
    httpAttributesExtractorBuilder.captureHttpHeaders(capturedHttpHeaders);
    return this;
  }

  /**
   * Configures the HTTP request headers that will be captured as span attributes.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  public ApacheHttpClientTracingBuilder setCapturedRequestHeaders(List<String> requestHeaders) {
    httpAttributesExtractorBuilder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures the HTTP response headers that will be captured as span attributes.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  public ApacheHttpClientTracingBuilder setCapturedResponseHeaders(List<String> responseHeaders) {
    httpAttributesExtractorBuilder.setCapturedResponseHeaders(responseHeaders);
    return this;
  }

  /**
   * Returns a new {@link ApacheHttpClientTracing} configured with this {@link
   * ApacheHttpClientTracingBuilder}.
   */
  public ApacheHttpClientTracing build() {
    ApacheHttpClientHttpAttributesGetter httpAttributesGetter =
        ApacheHttpClientHttpAttributesGetter.INSTANCE;
    ApacheHttpClientNetAttributesGetter netAttributesGetter =
        new ApacheHttpClientNetAttributesGetter();

    Instrumenter<ApacheHttpClientRequest, HttpResponse> instrumenter =
        Instrumenter.<ApacheHttpClientRequest, HttpResponse>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                HttpSpanNameExtractor.create(httpAttributesGetter))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(httpAttributesExtractorBuilder.build())
            .addAttributesExtractor(NetClientAttributesExtractor.create(netAttributesGetter))
            .addAttributesExtractors(additionalExtractors)
            // We manually inject because we need to inject internal requests for redirects.
            .newInstrumenter(SpanKindExtractor.alwaysClient());

    return new ApacheHttpClientTracing(instrumenter, openTelemetry.getPropagators());
  }
}
