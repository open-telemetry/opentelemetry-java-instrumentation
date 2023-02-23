/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3.server;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.server.ServerWebExchange;

public final class SpringWebfluxServerTelemetryBuilder {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-webflux-5.3";

  private final OpenTelemetry openTelemetry;
  private final HttpServerAttributesExtractorBuilder<ServerWebExchange, ServerWebExchange>
      httpAttributesExtractorBuilder =
          HttpServerAttributesExtractor.builder(
              SpringWebfluxHttpAttributesGetter.INSTANCE,
              SpringWebfluxNetAttributesGetter.INSTANCE);
  private final List<AttributesExtractor<ServerWebExchange, ServerWebExchange>>
      additionalExtractors = new ArrayList<>();

  SpringWebfluxServerTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items.
   */
  @CanIgnoreReturnValue
  public SpringWebfluxServerTelemetryBuilder addAttributesExtractor(
      AttributesExtractor<ServerWebExchange, ServerWebExchange> attributesExtractor) {
    additionalExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Configures the HTTP request headers that will be captured as span attributes.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public SpringWebfluxServerTelemetryBuilder setCapturedRequestHeaders(
      List<String> requestHeaders) {
    httpAttributesExtractorBuilder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures the HTTP response headers that will be captured as span attributes.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public SpringWebfluxServerTelemetryBuilder setCapturedResponseHeaders(
      List<String> responseHeaders) {
    httpAttributesExtractorBuilder.setCapturedResponseHeaders(responseHeaders);
    return this;
  }

  public SpringWebfluxServerTelemetry build() {
    SpringWebfluxHttpAttributesGetter attributesGetter = SpringWebfluxHttpAttributesGetter.INSTANCE;
    SpanNameExtractor<ServerWebExchange> spanNameExtractor =
        HttpSpanNameExtractor.create(attributesGetter);

    Instrumenter<ServerWebExchange, ServerWebExchange> instrumenter =
        Instrumenter.<ServerWebExchange, ServerWebExchange>builder(
                openTelemetry, INSTRUMENTATION_NAME, spanNameExtractor)
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(attributesGetter))
            .addAttributesExtractor(httpAttributesExtractorBuilder.build())
            .addAttributesExtractors(additionalExtractors)
            .buildServerInstrumenter(SpringWebfluxTextMapGetter.INSTANCE);

    return new SpringWebfluxServerTelemetry(instrumenter);
  }
}
