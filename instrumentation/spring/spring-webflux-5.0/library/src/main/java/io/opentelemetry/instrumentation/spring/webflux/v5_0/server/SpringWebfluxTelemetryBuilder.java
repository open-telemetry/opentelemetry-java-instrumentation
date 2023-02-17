/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_0.server;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;

public final class SpringWebfluxTelemetryBuilder {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-webflux-5.0";

  private final OpenTelemetry openTelemetry;
  private final HttpServerAttributesExtractorBuilder<ServerHttpRequest, ServerHttpResponse>
      httpAttributesExtractorBuilder =
          HttpServerAttributesExtractor.builder(
              SpringWebfluxHttpAttributesGetter.INSTANCE,
              SpringWebfluxNetAttributesGetter.INSTANCE);
  private final List<AttributesExtractor<ServerHttpRequest, ServerHttpResponse>>
      additionalExtractors = new ArrayList<>();

  SpringWebfluxTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items.
   */
  @CanIgnoreReturnValue
  public SpringWebfluxTelemetryBuilder addAttributesExtractor(
      AttributesExtractor<ServerHttpRequest, ServerHttpResponse> attributesExtractor) {
    additionalExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Configures the HTTP request headers that will be captured as span attributes.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public SpringWebfluxTelemetryBuilder setCapturedRequestHeaders(List<String> requestHeaders) {
    httpAttributesExtractorBuilder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures the HTTP response headers that will be captured as span attributes.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public SpringWebfluxTelemetryBuilder setCapturedResponseHeaders(List<String> responseHeaders) {
    httpAttributesExtractorBuilder.setCapturedResponseHeaders(responseHeaders);
    return this;
  }

  public SpringWebfluxServerTelemetry build() {
    SpringWebfluxHttpAttributesGetter attributesGetter = SpringWebfluxHttpAttributesGetter.INSTANCE;

    Instrumenter<ServerHttpRequest, ServerHttpResponse> instrumenter =
        Instrumenter.<ServerHttpRequest, ServerHttpResponse>builder(
                openTelemetry, INSTRUMENTATION_NAME, HttpSpanNameExtractor.create(attributesGetter))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(attributesGetter))
            .addAttributesExtractor(httpAttributesExtractorBuilder.build())
            .addAttributesExtractors(additionalExtractors)
            .buildServerInstrumenter(SpringWebfluxTextMapGetter.INSTANCE);

    return new SpringWebfluxServerTelemetry(instrumenter);
  }
}
