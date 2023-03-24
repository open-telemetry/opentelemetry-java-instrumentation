/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachehttpclient.v4_3;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.HttpResponse;

/** A builder for {@link ApacheHttpClientTelemetry}. */
public final class ApacheHttpClientTelemetryBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.apache-httpclient-4.3";

  private final OpenTelemetry openTelemetry;

  private final List<AttributesExtractor<? super ApacheHttpClientRequest, ? super HttpResponse>>
      additionalExtractors = new ArrayList<>();
  private final HttpClientAttributesExtractorBuilder<ApacheHttpClientRequest, HttpResponse>
      httpAttributesExtractorBuilder =
          HttpClientAttributesExtractor.builder(
              ApacheHttpClientHttpAttributesGetter.INSTANCE,
              new ApacheHttpClientNetAttributesGetter());

  ApacheHttpClientTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items. The {@link AttributesExtractor} will be executed after all default extractors.
   */
  @CanIgnoreReturnValue
  public ApacheHttpClientTelemetryBuilder addAttributeExtractor(
      AttributesExtractor<? super ApacheHttpClientRequest, ? super HttpResponse>
          attributesExtractor) {
    additionalExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Configures the HTTP request headers that will be captured as span attributes.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public ApacheHttpClientTelemetryBuilder setCapturedRequestHeaders(List<String> requestHeaders) {
    httpAttributesExtractorBuilder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures the HTTP response headers that will be captured as span attributes.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public ApacheHttpClientTelemetryBuilder setCapturedResponseHeaders(List<String> responseHeaders) {
    httpAttributesExtractorBuilder.setCapturedResponseHeaders(responseHeaders);
    return this;
  }

  /**
   * Returns a new {@link ApacheHttpClientTelemetry} configured with this {@link
   * ApacheHttpClientTelemetryBuilder}.
   */
  public ApacheHttpClientTelemetry build() {
    ApacheHttpClientHttpAttributesGetter httpAttributesGetter =
        ApacheHttpClientHttpAttributesGetter.INSTANCE;

    Instrumenter<ApacheHttpClientRequest, HttpResponse> instrumenter =
        Instrumenter.<ApacheHttpClientRequest, HttpResponse>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                HttpSpanNameExtractor.create(httpAttributesGetter))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(httpAttributesExtractorBuilder.build())
            .addAttributesExtractors(additionalExtractors)
            .addOperationMetrics(HttpClientMetrics.get())
            // We manually inject because we need to inject internal requests for redirects.
            .buildInstrumenter(SpanKindExtractor.alwaysClient());

    return new ApacheHttpClientTelemetry(instrumenter, openTelemetry.getPropagators());
  }
}
