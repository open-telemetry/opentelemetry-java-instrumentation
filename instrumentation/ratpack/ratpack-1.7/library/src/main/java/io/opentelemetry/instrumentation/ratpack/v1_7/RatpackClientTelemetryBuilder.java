/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.v1_7;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpClientInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.ratpack.v1_7.internal.Experimental;
import io.opentelemetry.instrumentation.ratpack.v1_7.internal.RatpackClientInstrumenterBuilderFactory;
import java.util.Collection;
import java.util.function.UnaryOperator;
import ratpack.http.client.HttpResponse;
import ratpack.http.client.RequestSpec;

/** Builder for {@link RatpackClientTelemetry}. */
public final class RatpackClientTelemetryBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.ratpack-1.7";

  private final DefaultHttpClientInstrumenterBuilder<RequestSpec, HttpResponse> builder;

  static {
    Experimental.internalSetEmitExperimentalClientTelemetry(
        (builder, emit) -> builder.builder.setEmitExperimentalHttpClientTelemetry(emit));
  }

  RatpackClientTelemetryBuilder(OpenTelemetry openTelemetry) {
    builder = RatpackClientInstrumenterBuilderFactory.create(INSTRUMENTATION_NAME, openTelemetry);
  }

  @CanIgnoreReturnValue
  public RatpackClientTelemetryBuilder addAttributesExtractor(
      AttributesExtractor<RequestSpec, HttpResponse> attributesExtractor) {
    builder.addAttributesExtractor(attributesExtractor);
    return this;
  }

  /**
   * Configures which HTTP request headers are captured as span attributes.
   *
   * <p>Header values are captured under the {@code http.request.header.<key>} attribute key. The
   * {@code <key>} part in the attribute key is the lowercase header name.
   *
   * <p>Header names are case-insensitive and matched literally; {@code *} and {@code ?} are not
   * treated as glob patterns. An empty collection captures no headers.
   */
  @CanIgnoreReturnValue
  public RatpackClientTelemetryBuilder setRequestCapturedHeaders(
      Collection<String> requestHeaders) {
    builder.setRequestCapturedHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures which HTTP response headers are captured as span attributes.
   *
   * <p>Header values are captured under the {@code http.response.header.<key>} attribute key. The
   * {@code <key>} part in the attribute key is the lowercase header name.
   *
   * <p>Header names are case-insensitive and matched literally; {@code *} and {@code ?} are not
   * treated as glob patterns. An empty collection captures no headers.
   */
  @CanIgnoreReturnValue
  public RatpackClientTelemetryBuilder setResponseCapturedHeaders(
      Collection<String> responseHeaders) {
    builder.setResponseCapturedHeaders(responseHeaders);
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
  public RatpackClientTelemetryBuilder setKnownMethods(Collection<String> knownMethods) {
    builder.setKnownMethods(knownMethods);
    return this;
  }

  /** Customizes the {@link SpanNameExtractor} by transforming the default instance. */
  @CanIgnoreReturnValue
  public RatpackClientTelemetryBuilder setSpanNameExtractorCustomizer(
      UnaryOperator<SpanNameExtractor<RequestSpec>> spanNameExtractorCustomizer) {
    builder.setSpanNameExtractorCustomizer(spanNameExtractorCustomizer);
    return this;
  }

  /** Returns a new instance with the configured settings. */
  public RatpackClientTelemetry build() {
    return new RatpackClientTelemetry(builder.build());
  }
}
