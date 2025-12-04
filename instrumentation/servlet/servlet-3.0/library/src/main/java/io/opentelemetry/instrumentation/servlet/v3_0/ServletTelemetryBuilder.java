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

/** A builder of {@link ServletTelemetry}. */
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
  }

  ServletTelemetryBuilder(OpenTelemetry openTelemetry) {
    servletBuilder =
        ServletInstrumenterBuilder.create(
            INSTRUMENTATION_NAME, openTelemetry, httpAttributesGetter, Servlet3Accessor.INSTANCE);
    builder = servletBuilder.getBuilder();
  }

  /**
   * Sets the status extractor for server spans.
   *
   * @deprecated Use {@link #setStatusExtractorCustomizer(UnaryOperator)} instead.
   */
  @Deprecated
  @CanIgnoreReturnValue
  public ServletTelemetryBuilder setStatusExtractor(
      UnaryOperator<SpanStatusExtractor<HttpServletRequest, HttpServletResponse>> statusExtractor) {
    return setStatusExtractorCustomizer(statusExtractor);
  }

  /**
   * Sets a customizer that receives the default {@link SpanStatusExtractor} and returns a
   * customized one.
   */
  @CanIgnoreReturnValue
  public ServletTelemetryBuilder setStatusExtractorCustomizer(
      UnaryOperator<SpanStatusExtractor<HttpServletRequest, HttpServletResponse>> statusExtractor) {
    builder.setStatusExtractorCustomizer(
        convertSpanStatusExtractor(
            statusExtractor,
            ServletRequestContext::new,
            ServletResponseContext::new,
            ServletRequestContext::request,
            ServletResponseContext::response));
    return this;
  }

  /**
   * Adds an extra {@link AttributesExtractor} to invoke to set attributes to instrumented items.
   * The {@link AttributesExtractor} will be executed after all default extractors.
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
   * Configures the HTTP server request headers that will be captured as span attributes.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public ServletTelemetryBuilder setCapturedRequestHeaders(Collection<String> requestHeaders) {
    builder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures the HTTP server response headers that will be captured as span attributes.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public ServletTelemetryBuilder setCapturedResponseHeaders(Collection<String> responseHeaders) {
    builder.setCapturedResponseHeaders(responseHeaders);
    return this;
  }

  /**
   * Configures the HTTP request parameters that will be captured as span attributes.
   *
   * @param captureRequestParameters A list of request parameter names.
   */
  @CanIgnoreReturnValue
  public ServletTelemetryBuilder setCapturedRequestParameters(
      List<String> captureRequestParameters) {
    servletBuilder.captureRequestParameters(captureRequestParameters);
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
   * @see HttpServerAttributesExtractorBuilder#setKnownMethods(Collection)
   */
  @CanIgnoreReturnValue
  public ServletTelemetryBuilder setKnownMethods(Collection<String> knownMethods) {
    builder.setKnownMethods(knownMethods);
    return this;
  }

  /**
   * Sets custom server {@link SpanNameExtractor} via transform function.
   *
   * @deprecated Use {@link #setSpanNameExtractorCustomizer(UnaryOperator)} instead.
   */
  @Deprecated
  @CanIgnoreReturnValue
  public ServletTelemetryBuilder setSpanNameExtractor(
      UnaryOperator<SpanNameExtractor<HttpServletRequest>> serverSpanNameExtractor) {
    return setSpanNameExtractorCustomizer(serverSpanNameExtractor);
  }

  /**
   * Sets a customizer that receives the default {@link SpanNameExtractor} and returns a customized
   * one.
   */
  @CanIgnoreReturnValue
  public ServletTelemetryBuilder setSpanNameExtractorCustomizer(
      UnaryOperator<SpanNameExtractor<HttpServletRequest>> spanNameExtractor) {
    builder.setSpanNameExtractorCustomizer(
        convertSpanNameExtractor(
            spanNameExtractor, ServletRequestContext::new, ServletRequestContext::request));
    return this;
  }

  public ServletTelemetry build() {
    return new ServletTelemetry(
        servletBuilder.build(HttpSpanNameExtractor.create(httpAttributesGetter)),
        addTraceIdRequestAttribute);
  }
}
