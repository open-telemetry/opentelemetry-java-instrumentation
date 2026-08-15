/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javahttpserver;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.sun.net.httpserver.HttpExchange;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpServerInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.javahttpserver.internal.Experimental;
import io.opentelemetry.instrumentation.javahttpserver.internal.JavaHttpServerInstrumenterBuilderUtil;
import java.util.Collection;
import java.util.function.UnaryOperator;

/** Builder for {@link JavaHttpServerTelemetry}. */
public final class JavaHttpServerTelemetryBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.java-http-server";

  private final DefaultHttpServerInstrumenterBuilder<HttpExchange, HttpExchange> builder;

  static {
    JavaHttpServerInstrumenterBuilderUtil.setServerBuilderExtractor(builder -> builder.builder);
    Experimental.internalSetEmitExperimentalTelemetry(
        (builder, emit) -> builder.builder.setEmitExperimentalHttpServerTelemetry(emit));
  }

  JavaHttpServerTelemetryBuilder(OpenTelemetry openTelemetry) {
    builder =
        DefaultHttpServerInstrumenterBuilder.create(
            INSTRUMENTATION_NAME,
            openTelemetry,
            new JavaHttpServerAttributesGetter(),
            new JavaHttpServerExchangeGetter());
  }

  /** Customizes the {@link SpanStatusExtractor} by transforming the default instance. */
  @CanIgnoreReturnValue
  public JavaHttpServerTelemetryBuilder setSpanStatusExtractorCustomizer(
      UnaryOperator<SpanStatusExtractor<HttpExchange, HttpExchange>>
          spanStatusExtractorCustomizer) {
    builder.setSpanStatusExtractorCustomizer(spanStatusExtractorCustomizer);
    return this;
  }

  /**
   * Adds an {@link AttributesExtractor} to extract attributes from requests and responses. Executed
   * after all default extractors.
   */
  @CanIgnoreReturnValue
  public JavaHttpServerTelemetryBuilder addAttributesExtractor(
      AttributesExtractor<HttpExchange, HttpExchange> attributesExtractor) {
    builder.addAttributesExtractor(attributesExtractor);
    return this;
  }

  /**
   * Configures which HTTP request headers are captured as span attributes.
   *
   * <p>Header values are captured under the {@code http.request.header.<key>} attribute key, where
   * {@code <key>} is the lowercase header name.
   *
   * <p>Selector patterns are matched case-insensitively, since HTTP header names are
   * case-insensitive. {@code ?} matches one character and {@code *} matches any number of
   * characters, including none. Excluded patterns take precedence over included patterns. A
   * selector with no included patterns captures every header that is not excluded, while an
   * {@linkplain IncludeExclude#isEmpty() empty} selector, like an absent one, captures no headers.
   */
  @CanIgnoreReturnValue
  public JavaHttpServerTelemetryBuilder setRequestHeaders(IncludeExclude requestHeaders) {
    builder.setRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures HTTP request headers to capture as span attributes.
   *
   * <p>The header names are matched literally. Unlike {@link #setRequestHeaders(IncludeExclude)},
   * {@code *} and {@code ?} are not treated as glob patterns, since this setting never supported
   * them as wildcards.
   *
   * @param requestHeaders HTTP header names to capture.
   * @deprecated Use {@link #setRequestHeaders(IncludeExclude)} instead, which matches glob patterns
   *     rather than literal header names. May be removed in the next minor release.
   */
  @Deprecated // may be removed in the next minor release
  @CanIgnoreReturnValue
  public JavaHttpServerTelemetryBuilder setCapturedRequestHeaders(
      Collection<String> requestHeaders) {
    builder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures which HTTP response headers are captured as span attributes.
   *
   * <p>Header values are captured under the {@code http.response.header.<key>} attribute key, where
   * {@code <key>} is the lowercase header name.
   *
   * <p>Selector patterns are matched case-insensitively, since HTTP header names are
   * case-insensitive. {@code ?} matches one character and {@code *} matches any number of
   * characters, including none. Excluded patterns take precedence over included patterns. A
   * selector with no included patterns captures every header that is not excluded, while an
   * {@linkplain IncludeExclude#isEmpty() empty} selector, like an absent one, captures no headers.
   */
  @CanIgnoreReturnValue
  public JavaHttpServerTelemetryBuilder setResponseHeaders(IncludeExclude responseHeaders) {
    builder.setResponseHeaders(responseHeaders);
    return this;
  }

  /**
   * Configures HTTP response headers to capture as span attributes.
   *
   * <p>The header names are matched literally. Unlike {@link #setResponseHeaders(IncludeExclude)},
   * {@code *} and {@code ?} are not treated as glob patterns, since this setting never supported
   * them as wildcards.
   *
   * @param responseHeaders HTTP header names to capture.
   * @deprecated Use {@link #setResponseHeaders(IncludeExclude)} instead, which matches glob
   *     patterns rather than literal header names. May be removed in the next minor release.
   */
  @Deprecated // may be removed in the next minor release
  @CanIgnoreReturnValue
  public JavaHttpServerTelemetryBuilder setCapturedResponseHeaders(
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
  public JavaHttpServerTelemetryBuilder setKnownMethods(Collection<String> knownMethods) {
    builder.setKnownMethods(knownMethods);
    return this;
  }

  /** Customizes the {@link SpanNameExtractor} by transforming the default instance. */
  @CanIgnoreReturnValue
  public JavaHttpServerTelemetryBuilder setSpanNameExtractorCustomizer(
      UnaryOperator<SpanNameExtractor<HttpExchange>> spanNameExtractorCustomizer) {
    builder.setSpanNameExtractorCustomizer(spanNameExtractorCustomizer);
    return this;
  }

  /** Returns a new instance with the configured settings. */
  public JavaHttpServerTelemetry build() {
    return new JavaHttpServerTelemetry(builder.build());
  }
}
