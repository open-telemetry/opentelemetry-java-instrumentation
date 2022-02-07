/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.CapturedHttpHeaders;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteHolder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesExtractor;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** A builder of {@link SpringWebMvcTracing}. */
public final class SpringWebMvcTracingBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-webmvc-3.1";

  private final OpenTelemetry openTelemetry;
  private final List<AttributesExtractor<HttpServletRequest, HttpServletResponse>>
      additionalExtractors = new ArrayList<>();
  private CapturedHttpHeaders capturedHttpHeaders = CapturedHttpHeaders.server(Config.get());

  SpringWebMvcTracingBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items.
   */
  public SpringWebMvcTracingBuilder addAttributesExtractor(
      AttributesExtractor<HttpServletRequest, HttpServletResponse> attributesExtractor) {
    additionalExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Configure the instrumentation to capture chosen HTTP request and response headers as span
   * attributes.
   *
   * @param capturedHttpHeaders An instance of {@link CapturedHttpHeaders} containing the configured
   *     HTTP request and response names.
   */
  public SpringWebMvcTracingBuilder captureHttpHeaders(CapturedHttpHeaders capturedHttpHeaders) {
    this.capturedHttpHeaders = capturedHttpHeaders;
    return this;
  }

  /**
   * Returns a new {@link SpringWebMvcTracing} with the settings of this {@link
   * SpringWebMvcTracingBuilder}.
   */
  public SpringWebMvcTracing build() {
    SpringWebMvcHttpAttributesGetter httpAttributesGetter = new SpringWebMvcHttpAttributesGetter();

    Instrumenter<HttpServletRequest, HttpServletResponse> instrumenter =
        Instrumenter.<HttpServletRequest, HttpServletResponse>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                HttpSpanNameExtractor.create(httpAttributesGetter))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(
                HttpServerAttributesExtractor.create(httpAttributesGetter, capturedHttpHeaders))
            .addAttributesExtractor(new StatusCodeExtractor())
            .addAttributesExtractor(
                NetServerAttributesExtractor.create(new SpringWebMvcNetAttributesGetter()))
            .addAttributesExtractors(additionalExtractors)
            .addRequestMetrics(HttpServerMetrics.get())
            .addContextCustomizer(HttpRouteHolder.get())
            .newServerInstrumenter(JavaxHttpServletRequestGetter.INSTANCE);

    return new SpringWebMvcTracing(instrumenter);
  }
}
