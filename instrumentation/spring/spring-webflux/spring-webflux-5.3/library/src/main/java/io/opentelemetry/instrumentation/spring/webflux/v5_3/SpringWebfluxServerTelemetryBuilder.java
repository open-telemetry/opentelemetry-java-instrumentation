/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpServerInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.internal.Experimental;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.internal.SpringWebfluxBuilderUtil;
import java.util.Collection;
import java.util.function.UnaryOperator;
import org.springframework.web.server.ServerWebExchange;

/** Builder for {@link SpringWebfluxServerTelemetry}. */
public final class SpringWebfluxServerTelemetryBuilder {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-webflux-5.3";

  private final DefaultHttpServerInstrumenterBuilder<ServerWebExchange, ServerWebExchange> builder;

  static {
    SpringWebfluxBuilderUtil.setServerBuilderExtractor(builder -> builder.builder);
    Experimental.internalSetEmitExperimentalServerTelemetry(
        (builder, emit) -> builder.builder.setEmitExperimentalHttpServerTelemetry(emit));
  }

  SpringWebfluxServerTelemetryBuilder(OpenTelemetry openTelemetry) {
    builder =
        DefaultHttpServerInstrumenterBuilder.create(
            INSTRUMENTATION_NAME,
            openTelemetry,
            WebfluxServerHttpAttributesGetter.INSTANCE,
            WebfluxTextMapGetter.INSTANCE);
  }

  /**
   * Adds an {@link AttributesExtractor} to extract attributes from requests and responses. Executed
   * after all default extractors.
   */
  @CanIgnoreReturnValue
  public SpringWebfluxServerTelemetryBuilder addAttributesExtractor(
      AttributesExtractor<ServerWebExchange, ServerWebExchange> attributesExtractor) {
    builder.addAttributesExtractor(attributesExtractor);
    return this;
  }

  /**
   * Configures HTTP request headers to capture as span attributes.
   *
   * @param requestHeaders HTTP header names to capture.
   */
  @CanIgnoreReturnValue
  public SpringWebfluxServerTelemetryBuilder setCapturedRequestHeaders(
      Collection<String> requestHeaders) {
    builder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures HTTP response headers to capture as span attributes.
   *
   * @param responseHeaders HTTP header names to capture.
   */
  @CanIgnoreReturnValue
  public SpringWebfluxServerTelemetryBuilder setCapturedResponseHeaders(
      Collection<String> responseHeaders) {
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
  public SpringWebfluxServerTelemetryBuilder setKnownMethods(Collection<String> knownMethods) {
    builder.setKnownMethods(knownMethods);
    return this;
  }

  /** Customizes the {@link SpanNameExtractor} by transforming the default instance. */
  @CanIgnoreReturnValue
  public SpringWebfluxServerTelemetryBuilder setSpanNameExtractorCustomizer(
      UnaryOperator<SpanNameExtractor<ServerWebExchange>> spanNameExtractorCustomizer) {
    builder.setSpanNameExtractorCustomizer(spanNameExtractorCustomizer);
    return this;
  }

  /** Returns a new instance with the configured settings. */
  public SpringWebfluxServerTelemetry build() {
    return new SpringWebfluxServerTelemetry(builder.build());
  }
}
