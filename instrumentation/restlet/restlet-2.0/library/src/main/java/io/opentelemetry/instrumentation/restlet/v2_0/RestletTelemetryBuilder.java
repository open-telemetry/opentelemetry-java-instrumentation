/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v2_0;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpServerInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.restlet.v2_0.internal.Experimental;
import io.opentelemetry.instrumentation.restlet.v2_0.internal.RestletTelemetryBuilderFactory;
import java.util.Collection;
import java.util.function.UnaryOperator;
import org.restlet.Request;
import org.restlet.Response;

/** Builder for {@link RestletTelemetry}. */
public final class RestletTelemetryBuilder {

  private final DefaultHttpServerInstrumenterBuilder<Request, Response> builder;

  static {
    Experimental.internalSetEmitExperimentalTelemetry(
        (builder, emit) -> builder.builder.setEmitExperimentalHttpServerTelemetry(emit));
  }

  RestletTelemetryBuilder(OpenTelemetry openTelemetry) {
    builder = RestletTelemetryBuilderFactory.create(openTelemetry);
  }

  /**
   * Adds an {@link AttributesExtractor} to extract attributes from requests and responses. Executed
   * after all default extractors.
   */
  @CanIgnoreReturnValue
  public RestletTelemetryBuilder addAttributesExtractor(
      AttributesExtractor<Request, Response> attributesExtractor) {
    builder.addAttributesExtractor(attributesExtractor);
    return this;
  }

  /**
   * Configures which HTTP request headers are captured as span attributes.
   *
   * <p>Selector patterns are matched case-insensitively, since HTTP header names are
   * case-insensitive. {@code ?} matches one character and {@code *} matches any number of
   * characters, including none. Excluded patterns take precedence over included patterns. A
   * selector with no included patterns captures every header that is not excluded, and an
   * {@linkplain IncludeExclude#isEmpty() empty} selector captures no headers. No request headers
   * are captured unless this selector is configured.
   *
   * @param requestHeaders Selector for the HTTP request headers to capture.
   */
  @CanIgnoreReturnValue
  public RestletTelemetryBuilder setRequestHeaders(IncludeExclude requestHeaders) {
    builder.setRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures HTTP request headers to capture as span attributes.
   *
   * @param requestHeaders HTTP header names to capture.
   * @deprecated Use {@link #setRequestHeaders(IncludeExclude)} instead. May be removed in the next
   *     minor release.
   */
  @Deprecated // may be removed in the next minor release
  @CanIgnoreReturnValue
  public RestletTelemetryBuilder setCapturedRequestHeaders(Collection<String> requestHeaders) {
    return setRequestHeaders(IncludeExclude.builder().setIncluded(requestHeaders).build());
  }

  /**
   * Configures which HTTP response headers are captured as span attributes.
   *
   * <p>Selector patterns are matched case-insensitively, since HTTP header names are
   * case-insensitive. {@code ?} matches one character and {@code *} matches any number of
   * characters, including none. Excluded patterns take precedence over included patterns. A
   * selector with no included patterns captures every header that is not excluded, and an
   * {@linkplain IncludeExclude#isEmpty() empty} selector captures no headers. No response headers
   * are captured unless this selector is configured.
   *
   * @param responseHeaders Selector for the HTTP response headers to capture.
   */
  @CanIgnoreReturnValue
  public RestletTelemetryBuilder setResponseHeaders(IncludeExclude responseHeaders) {
    builder.setResponseHeaders(responseHeaders);
    return this;
  }

  /**
   * Configures HTTP response headers to capture as span attributes.
   *
   * @param responseHeaders HTTP header names to capture.
   * @deprecated Use {@link #setResponseHeaders(IncludeExclude)} instead. May be removed in the next
   *     minor release.
   */
  @Deprecated // may be removed in the next minor release
  @CanIgnoreReturnValue
  public RestletTelemetryBuilder setCapturedResponseHeaders(Collection<String> responseHeaders) {
    return setResponseHeaders(IncludeExclude.builder().setIncluded(responseHeaders).build());
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
  public RestletTelemetryBuilder setKnownMethods(Collection<String> knownMethods) {
    builder.setKnownMethods(knownMethods);
    return this;
  }

  /** Customizes the {@link SpanNameExtractor} by transforming the default instance. */
  @CanIgnoreReturnValue
  public RestletTelemetryBuilder setSpanNameExtractorCustomizer(
      UnaryOperator<SpanNameExtractor<Request>> spanNameExtractorCustomizer) {
    builder.setSpanNameExtractorCustomizer(spanNameExtractorCustomizer);
    return this;
  }

  /** Returns a new instance with the configured settings. */
  public RestletTelemetry build() {
    return new RestletTelemetry(builder.build());
  }
}
