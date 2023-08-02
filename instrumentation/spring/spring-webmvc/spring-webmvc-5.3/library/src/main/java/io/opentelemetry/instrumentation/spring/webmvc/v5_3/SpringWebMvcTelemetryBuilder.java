/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc.v5_3;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteHolder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** A builder of {@link SpringWebMvcTelemetry}. */
public final class SpringWebMvcTelemetryBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-webmvc-5.3";

  private final OpenTelemetry openTelemetry;
  private final List<AttributesExtractor<HttpServletRequest, HttpServletResponse>>
      additionalExtractors = new ArrayList<>();
  private final HttpServerAttributesExtractorBuilder<HttpServletRequest, HttpServletResponse>
      httpAttributesExtractorBuilder =
          HttpServerAttributesExtractor.builder(SpringWebMvcHttpAttributesGetter.INSTANCE);

  @Nullable
  private Function<
          SpanNameExtractor<HttpServletRequest>,
          ? extends SpanNameExtractor<? super HttpServletRequest>>
      spanNameExtractorTransformer;

  SpringWebMvcTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items.
   */
  @CanIgnoreReturnValue
  public SpringWebMvcTelemetryBuilder addAttributesExtractor(
      AttributesExtractor<HttpServletRequest, HttpServletResponse> attributesExtractor) {
    additionalExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Configures the HTTP request headers that will be captured as span attributes.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public SpringWebMvcTelemetryBuilder setCapturedRequestHeaders(List<String> requestHeaders) {
    httpAttributesExtractorBuilder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures the HTTP response headers that will be captured as span attributes.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public SpringWebMvcTelemetryBuilder setCapturedResponseHeaders(List<String> responseHeaders) {
    httpAttributesExtractorBuilder.setCapturedResponseHeaders(responseHeaders);
    return this;
  }

  /** Sets custom {@link SpanNameExtractor} via transform function. */
  @CanIgnoreReturnValue
  public SpringWebMvcTelemetryBuilder setSpanNameExtractor(
      Function<
              SpanNameExtractor<HttpServletRequest>,
              ? extends SpanNameExtractor<? super HttpServletRequest>>
          spanNameExtractor) {
    this.spanNameExtractorTransformer = spanNameExtractor;
    return this;
  }

  /**
   * Configures the instrumentation to recognize an alternative set of HTTP request methods.
   *
   * <p>By default, this instrumentation defines "known" methods as the ones listed in <a
   * href="https://www.rfc-editor.org/rfc/rfc9110.html#name-methods">RFC9110</a> and the PATCH
   * method defined in <a href="https://www.rfc-editor.org/rfc/rfc5789.html">RFC5789</a>.
   *
   * <p>Note: calling this method <b>overrides</b> the default known method sets completely; it does
   * not supplement it.
   *
   * @param knownMethods A set of recognized HTTP request methods.
   * @see HttpServerAttributesExtractorBuilder#setKnownMethods(Set)
   */
  @CanIgnoreReturnValue
  public SpringWebMvcTelemetryBuilder setKnownMethods(Set<String> knownMethods) {
    httpAttributesExtractorBuilder.setKnownMethods(knownMethods);
    return this;
  }

  /**
   * Returns a new {@link SpringWebMvcTelemetry} with the settings of this {@link
   * SpringWebMvcTelemetryBuilder}.
   */
  public SpringWebMvcTelemetry build() {
    SpringWebMvcHttpAttributesGetter httpAttributesGetter =
        SpringWebMvcHttpAttributesGetter.INSTANCE;

    SpanNameExtractor<HttpServletRequest> originalSpanNameExtractor =
        HttpSpanNameExtractor.create(httpAttributesGetter);
    SpanNameExtractor<? super HttpServletRequest> spanNameExtractor = originalSpanNameExtractor;
    if (spanNameExtractorTransformer != null) {
      spanNameExtractor = spanNameExtractorTransformer.apply(originalSpanNameExtractor);
    }

    Instrumenter<HttpServletRequest, HttpServletResponse> instrumenter =
        Instrumenter.<HttpServletRequest, HttpServletResponse>builder(
                openTelemetry, INSTRUMENTATION_NAME, spanNameExtractor)
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(httpAttributesExtractorBuilder.build())
            .addAttributesExtractors(additionalExtractors)
            .addOperationMetrics(HttpServerMetrics.get())
            .addContextCustomizer(HttpRouteHolder.create(httpAttributesGetter))
            .buildServerInstrumenter(JavaxHttpServletRequestGetter.INSTANCE);

    return new SpringWebMvcTelemetry(instrumenter);
  }
}
