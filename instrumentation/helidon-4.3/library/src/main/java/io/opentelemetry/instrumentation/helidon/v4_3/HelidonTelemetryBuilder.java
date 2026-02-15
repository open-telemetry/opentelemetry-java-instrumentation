/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.helidon.v4_3;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpServerInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.helidon.v4_3.internal.Experimental;
import io.opentelemetry.instrumentation.helidon.v4_3.internal.HelidonInstrumenterBuilderUtil;
import java.util.Collection;
import java.util.function.UnaryOperator;

/** Builder for {@link HelidonTelemetry}. */
public final class HelidonTelemetryBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.helidon-4.3";

  private final DefaultHttpServerInstrumenterBuilder<ServerRequest, ServerResponse> builder;

  static {
    HelidonInstrumenterBuilderUtil.setServerBuilderExtractor(builder -> builder.builder);
    Experimental.internalSetEmitExperimentalTelemetry(
        (builder, emit) -> builder.builder.setEmitExperimentalHttpServerTelemetry(emit));
  }

  HelidonTelemetryBuilder(OpenTelemetry openTelemetry) {
    builder =
        DefaultHttpServerInstrumenterBuilder.create(
            INSTRUMENTATION_NAME,
            openTelemetry,
            HelidonAttributesGetter.INSTANCE,
            HelidonRequestGetter.INSTANCE);
  }

  /** Customizes the {@link SpanStatusExtractor} by transforming the default instance. */
  @CanIgnoreReturnValue
  public HelidonTelemetryBuilder setSpanStatusExtractorCustomizer(
      UnaryOperator<SpanStatusExtractor<ServerRequest, ServerResponse>>
          spanStatusExtractorCustomizer) {
    builder.setSpanStatusExtractorCustomizer(spanStatusExtractorCustomizer);
    return this;
  }

  /**
   * Adds an {@link AttributesExtractor} to extract attributes from requests and responses. Executed
   * after all default extractors.
   */
  @CanIgnoreReturnValue
  public HelidonTelemetryBuilder addAttributesExtractor(
      AttributesExtractor<ServerRequest, ServerResponse> attributesExtractor) {
    builder.addAttributesExtractor(attributesExtractor);
    return this;
  }

  /**
   * Configures HTTP request headers to capture as span attributes.
   *
   * @param requestHeaders HTTP header names to capture.
   */
  @CanIgnoreReturnValue
  public HelidonTelemetryBuilder setCapturedRequestHeaders(Collection<String> requestHeaders) {
    builder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures HTTP response headers to capture as span attributes.
   *
   * @param responseHeaders HTTP header names to capture.
   */
  @CanIgnoreReturnValue
  public HelidonTelemetryBuilder setCapturedResponseHeaders(Collection<String> responseHeaders) {
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
  public HelidonTelemetryBuilder setKnownMethods(Collection<String> knownMethods) {
    builder.setKnownMethods(knownMethods);
    return this;
  }

  /** Customizes the {@link SpanNameExtractor} by transforming the default instance. */
  @CanIgnoreReturnValue
  public HelidonTelemetryBuilder setSpanNameExtractorCustomizer(
      UnaryOperator<SpanNameExtractor<ServerRequest>> spanNameExtractorCustomizer) {
    builder.setSpanNameExtractorCustomizer(spanNameExtractorCustomizer);
    return this;
  }

  public HelidonTelemetry build() {
    return new HelidonTelemetry(builder.build());
  }
}
