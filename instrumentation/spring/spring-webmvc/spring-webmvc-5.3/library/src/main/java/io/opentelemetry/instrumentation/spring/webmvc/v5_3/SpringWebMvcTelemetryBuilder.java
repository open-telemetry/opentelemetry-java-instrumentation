/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc.v5_3;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpServerInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.spring.webmvc.v5_3.internal.Experimental;
import io.opentelemetry.instrumentation.spring.webmvc.v5_3.internal.SpringMvcBuilderUtil;
import java.util.Collection;
import java.util.function.UnaryOperator;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Builder for {@link SpringWebMvcTelemetry}. */
public final class SpringWebMvcTelemetryBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-webmvc-5.3";

  private final DefaultHttpServerInstrumenterBuilder<HttpServletRequest, HttpServletResponse>
      builder;

  static {
    SpringMvcBuilderUtil.setBuilderExtractor(builder -> builder.builder);
    Experimental.internalSetEmitExperimentalTelemetry(
        (builder, emit) -> builder.builder.setEmitExperimentalHttpServerTelemetry(emit));
  }

  SpringWebMvcTelemetryBuilder(OpenTelemetry openTelemetry) {
    builder =
        DefaultHttpServerInstrumenterBuilder.create(
            INSTRUMENTATION_NAME,
            openTelemetry,
            SpringWebMvcHttpAttributesGetter.INSTANCE,
            JavaxHttpServletRequestGetter.INSTANCE);
  }

  /**
   * Adds an {@link AttributesExtractor} to extract attributes from requests and responses. Executed
   * after all default extractors.
   */
  @CanIgnoreReturnValue
  public SpringWebMvcTelemetryBuilder addAttributesExtractor(
      AttributesExtractor<HttpServletRequest, HttpServletResponse> attributesExtractor) {
    builder.addAttributesExtractor(attributesExtractor);
    return this;
  }

  /**
   * Configures HTTP request headers to capture as span attributes.
   *
   * @param requestHeaders HTTP header names to capture.
   */
  @CanIgnoreReturnValue
  public SpringWebMvcTelemetryBuilder setCapturedRequestHeaders(Collection<String> requestHeaders) {
    builder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures HTTP response headers to capture as span attributes.
   *
   * @param responseHeaders HTTP header names to capture.
   */
  @CanIgnoreReturnValue
  public SpringWebMvcTelemetryBuilder setCapturedResponseHeaders(
      Collection<String> responseHeaders) {
    builder.setCapturedResponseHeaders(responseHeaders);
    return this;
  }

  /** Customizes the {@link SpanNameExtractor} by transforming the default instance. */
  @CanIgnoreReturnValue
  public SpringWebMvcTelemetryBuilder setSpanNameExtractorCustomizer(
      UnaryOperator<SpanNameExtractor<HttpServletRequest>> spanNameExtractorCustomizer) {
    builder.setSpanNameExtractorCustomizer(spanNameExtractorCustomizer);
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
  public SpringWebMvcTelemetryBuilder setKnownMethods(Collection<String> knownMethods) {
    builder.setKnownMethods(knownMethods);
    return this;
  }

  /** Returns a new instance with the configured settings. */
  public SpringWebMvcTelemetry build() {
    return new SpringWebMvcTelemetry(builder.build());
  }
}
