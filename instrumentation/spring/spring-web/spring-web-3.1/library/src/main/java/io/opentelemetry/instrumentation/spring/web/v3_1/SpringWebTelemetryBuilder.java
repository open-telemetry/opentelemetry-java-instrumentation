/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.web.v3_1;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpClientInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.spring.web.v3_1.internal.Experimental;
import io.opentelemetry.instrumentation.spring.web.v3_1.internal.WebTelemetryUtil;
import java.util.Collection;
import java.util.function.UnaryOperator;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;

/** Builder for {@link SpringWebTelemetry}. */
public final class SpringWebTelemetryBuilder {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-web-3.1";
  private final DefaultHttpClientInstrumenterBuilder<HttpRequest, ClientHttpResponse> builder;

  static {
    WebTelemetryUtil.setBuilderExtractor(SpringWebTelemetryBuilder::getBuilder);
    Experimental.internalSetEmitExperimentalTelemetry(
        (builder, emit) -> builder.builder.setEmitExperimentalHttpClientTelemetry(emit));
  }

  SpringWebTelemetryBuilder(OpenTelemetry openTelemetry) {
    builder =
        DefaultHttpClientInstrumenterBuilder.create(
            INSTRUMENTATION_NAME,
            openTelemetry,
            SpringWebHttpAttributesGetter.INSTANCE,
            HttpRequestSetter.INSTANCE);
  }

  private DefaultHttpClientInstrumenterBuilder<HttpRequest, ClientHttpResponse> getBuilder() {
    return builder;
  }

  /**
   * Adds an {@link AttributesExtractor} to extract attributes from requests and responses. Executed
   * after all default extractors.
   */
  @CanIgnoreReturnValue
  public SpringWebTelemetryBuilder addAttributesExtractor(
      AttributesExtractor<HttpRequest, ClientHttpResponse> attributesExtractor) {
    builder.addAttributesExtractor(attributesExtractor);
    return this;
  }

  /**
   * Configures HTTP request headers to capture as span attributes.
   *
   * @param requestHeaders HTTP header names to capture.
   */
  @CanIgnoreReturnValue
  public SpringWebTelemetryBuilder setCapturedRequestHeaders(Collection<String> requestHeaders) {
    builder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures HTTP response headers to capture as span attributes.
   *
   * @param responseHeaders HTTP header names to capture.
   */
  @CanIgnoreReturnValue
  public SpringWebTelemetryBuilder setCapturedResponseHeaders(Collection<String> responseHeaders) {
    builder.setCapturedResponseHeaders(responseHeaders);
    return this;
  }

  /** Customizes the {@link SpanNameExtractor} by transforming the default instance. */
  @CanIgnoreReturnValue
  public SpringWebTelemetryBuilder setSpanNameExtractorCustomizer(
      UnaryOperator<SpanNameExtractor<HttpRequest>> spanNameExtractorCustomizer) {
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
   * @see HttpClientAttributesExtractorBuilder#setKnownMethods(Collection)
   */
  @CanIgnoreReturnValue
  public SpringWebTelemetryBuilder setKnownMethods(Collection<String> knownMethods) {
    builder.setKnownMethods(knownMethods);
    return this;
  }

  /** Returns a new instance with the configured settings. */
  public SpringWebTelemetry build() {
    return new SpringWebTelemetry(builder.build());
  }
}
