/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.web;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;

/** A builder of {@link SpringWebTelemetry}. */
public final class SpringWebTelemetryBuilder {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-web-3.1";

  private final OpenTelemetry openTelemetry;
  private final List<AttributesExtractor<HttpRequest, ClientHttpResponse>> additionalExtractors =
      new ArrayList<>();
  private final HttpClientAttributesExtractorBuilder<HttpRequest, ClientHttpResponse>
      httpAttributesExtractorBuilder =
          HttpClientAttributesExtractor.builder(SpringWebHttpAttributesGetter.INSTANCE);

  SpringWebTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items.
   */
  public SpringWebTelemetryBuilder addAttributesExtractor(
      AttributesExtractor<HttpRequest, ClientHttpResponse> attributesExtractor) {
    additionalExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Configures the HTTP request headers that will be captured as span attributes.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  public SpringWebTelemetryBuilder setCapturedRequestHeaders(List<String> requestHeaders) {
    httpAttributesExtractorBuilder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures the HTTP response headers that will be captured as span attributes.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  public SpringWebTelemetryBuilder setCapturedResponseHeaders(List<String> responseHeaders) {
    httpAttributesExtractorBuilder.setCapturedResponseHeaders(responseHeaders);
    return this;
  }

  /**
   * Returns a new {@link SpringWebTelemetry} with the settings of this {@link
   * SpringWebTelemetryBuilder}.
   */
  public SpringWebTelemetry build() {
    SpringWebHttpAttributesGetter httpAttributeGetter = SpringWebHttpAttributesGetter.INSTANCE;
    SpringWebNetAttributesGetter netAttributesGetter = new SpringWebNetAttributesGetter();

    Instrumenter<HttpRequest, ClientHttpResponse> instrumenter =
        Instrumenter.<HttpRequest, ClientHttpResponse>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                HttpSpanNameExtractor.create(httpAttributeGetter))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributeGetter))
            .addAttributesExtractor(httpAttributesExtractorBuilder.build())
            .addAttributesExtractor(NetClientAttributesExtractor.create(netAttributesGetter))
            .addAttributesExtractors(additionalExtractors)
            .addRequestMetrics(HttpClientMetrics.get())
            .newClientInstrumenter(HttpRequestSetter.INSTANCE);

    return new SpringWebTelemetry(instrumenter);
  }
}
