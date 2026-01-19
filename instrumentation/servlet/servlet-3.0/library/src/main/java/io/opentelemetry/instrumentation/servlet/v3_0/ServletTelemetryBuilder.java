/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v3_0;

import static io.opentelemetry.instrumentation.api.internal.InstrumenterUtil.convertAttributesExtractor;
import static io.opentelemetry.instrumentation.api.internal.InstrumenterUtil.convertSpanNameExtractor;
import static io.opentelemetry.instrumentation.api.internal.InstrumenterUtil.convertSpanStatusExtractor;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpServerInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.servlet.internal.ServletHttpAttributesGetter;
import io.opentelemetry.instrumentation.servlet.internal.ServletInstrumenterBuilder;
import io.opentelemetry.instrumentation.servlet.internal.ServletRequestContext;
import io.opentelemetry.instrumentation.servlet.internal.ServletResponseContext;
import io.opentelemetry.instrumentation.servlet.v3_0.internal.Experimental;
import io.opentelemetry.instrumentation.servlet.v3_0.internal.Servlet3Accessor;
import java.util.Collection;
import java.util.List;
import java.util.function.UnaryOperator;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Builder for {@link ServletTelemetry}. */
public final class ServletTelemetryBuilder {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.servlet-3.0";

  private final HttpServerAttributesGetter<
          ServletRequestContext<HttpServletRequest>, ServletResponseContext<HttpServletResponse>>
      httpAttributesGetter = new ServletHttpAttributesGetter<>(Servlet3Accessor.INSTANCE);
  private final DefaultHttpServerInstrumenterBuilder<
          ServletRequestContext<HttpServletRequest>, ServletResponseContext<HttpServletResponse>>
      builder;
  private final ServletInstrumenterBuilder<HttpServletRequest, HttpServletResponse> servletBuilder;
  private boolean addTraceIdRequestAttribute = false;

  static {
    Experimental.internalSetEmitExperimentalTelemetry(
        (builder, emit) -> builder.builder.setEmitExperimentalHttpServerTelemetry(emit));
    Experimental.internalSetAddTraceIdRequestAttribute(
        (builder, value) -> builder.addTraceIdRequestAttribute = value);
    Experimental.internalSetCapturedRequestParameters(
        (builder, params) -> builder.servletBuilder.setCaptureRequestParameters(params));
    Experimental.internalSetCaptureEnduserId(
        (builder, value) -> builder.servletBuilder.setCaptureEnduserId(value));
  }

  ServletTelemetryBuilder(OpenTelemetry openTelemetry) {
    servletBuilder =
        ServletInstrumenterBuilder.create(
            INSTRUMENTATION_NAME, openTelemetry, httpAttributesGetter, Servlet3Accessor.INSTANCE);
    builder = servletBuilder.getBuilder();
  }

  /** Customizes the {@link SpanStatusExtractor} by transforming the default instance. */
  @CanIgnoreReturnValue
  public ServletTelemetryBuilder setSpanStatusExtractorCustomizer(
      UnaryOperator<SpanStatusExtractor<HttpServletRequest, HttpServletResponse>>
          spanStatusExtractorCustomizer) {
    builder.setSpanStatusExtractorCustomizer(
        convertSpanStatusExtractor(
            spanStatusExtractorCustomizer,
            ServletRequestContext::new,
            ServletResponseContext::new,
            ServletRequestContext::request,
            ServletResponseContext::response));
    return this;
  }

  /**
   * Adds an {@link AttributesExtractor} to extract attributes from requests and responses. Executed
   * after all default extractors.
   */
  @CanIgnoreReturnValue
  public ServletTelemetryBuilder addAttributesExtractor(
      AttributesExtractor<HttpServletRequest, HttpServletResponse> attributesExtractor) {
    builder.addAttributesExtractor(
        convertAttributesExtractor(
            attributesExtractor, ServletRequestContext::request, ServletResponseContext::response));
    return this;
  }

  /**
   * Configures HTTP request headers to capture as span attributes.
   *
   * @param requestHeaders HTTP header names to capture.
   */
  @CanIgnoreReturnValue
  public ServletTelemetryBuilder setCapturedRequestHeaders(Collection<String> requestHeaders) {
    builder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures HTTP response headers to capture as span attributes.
   *
   * @param responseHeaders HTTP header names to capture.
   */
  @CanIgnoreReturnValue
  public ServletTelemetryBuilder setCapturedResponseHeaders(Collection<String> responseHeaders) {
    builder.setCapturedResponseHeaders(responseHeaders);
    return this;
  }

  /**
   * Configures recognized HTTP request methods.
   *
   * <p>By default, recognizes methods from <a
   * href="https://www.rfc-editor.org/rfc/rfc9110.html#name-methods">RFC9110</a> and PATCH from <a
   * href="https://www.rfc-editor.org/rfc/rfc5789.html">RFC5789</a>.
   *
   * <p><b>Note:</b> This <b>overrides</b> defaults completely; it does not supplement them.
   *
   * @param knownMethods HTTP request methods to recognize.
   * @see HttpServerAttributesExtractorBuilder#setKnownMethods(Collection)
   */
  @CanIgnoreReturnValue
  public ServletTelemetryBuilder setKnownMethods(Collection<String> knownMethods) {
    builder.setKnownMethods(knownMethods);
    return this;
  }

  /** Customizes the {@link SpanNameExtractor} by transforming the default instance. */
  @CanIgnoreReturnValue
  public ServletTelemetryBuilder setSpanNameExtractorCustomizer(
      UnaryOperator<SpanNameExtractor<HttpServletRequest>> spanNameExtractorCustomizer) {
    builder.setSpanNameExtractorCustomizer(
        convertSpanNameExtractor(
            spanNameExtractorCustomizer,
            ServletRequestContext::new,
            ServletRequestContext::request));
    return this;
  }

  /** Returns a new instance with the configured settings. */
  public ServletTelemetry build() {
    return new ServletTelemetry(
        servletBuilder.build(HttpSpanNameExtractor.create(httpAttributesGetter)),
        addTraceIdRequestAttribute);
  }
}
