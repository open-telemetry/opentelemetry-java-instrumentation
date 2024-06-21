/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v1_1;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpServerTelemetryBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesExtractorBuilder;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.restlet.data.Request;
import org.restlet.data.Response;

/** A builder of {@link RestletTelemetry}. */
public final class RestletTelemetryBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.restlet-1.1";

  private final DefaultHttpServerTelemetryBuilder<Request, Response> serverBuilder;

  RestletTelemetryBuilder(OpenTelemetry openTelemetry) {
    serverBuilder = new DefaultHttpServerTelemetryBuilder<>(INSTRUMENTATION_NAME, openTelemetry,  RestletHttpAttributesGetter.INSTANCE, Optional.of(RestletHeadersGetter.INSTANCE));
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items.
   */
  @CanIgnoreReturnValue
  public RestletTelemetryBuilder addAttributesExtractor(
      AttributesExtractor<Request, Response> attributesExtractor) {
    serverBuilder.addAttributesExtractor(attributesExtractor);
    return this;
  }

  /**
   * Configures the HTTP request headers that will be captured as span attributes.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public RestletTelemetryBuilder setCapturedRequestHeaders(List<String> requestHeaders) {
    serverBuilder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures the HTTP response headers that will be captured as span attributes.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public RestletTelemetryBuilder setCapturedResponseHeaders(List<String> responseHeaders) {
    serverBuilder.setCapturedResponseHeaders(responseHeaders);
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
  public RestletTelemetryBuilder setKnownMethods(Set<String> knownMethods) {
    serverBuilder.setKnownMethods(knownMethods);
    return this;
  }

  /**
   * Configures the instrumentation to emit experimental HTTP server metrics.
   *
   * @param emitExperimentalHttpServerMetrics {@code true} if the experimental HTTP server metrics
   *     are to be emitted.
   */
  @CanIgnoreReturnValue
  public RestletTelemetryBuilder setEmitExperimentalHttpServerMetrics(
      boolean emitExperimentalHttpServerMetrics) {
    serverBuilder.setEmitExperimentalHttpServerMetrics(emitExperimentalHttpServerMetrics);
    return this;
  }

  /** Sets custom {@link SpanNameExtractor} via transform function. */
  @CanIgnoreReturnValue
  public RestletTelemetryBuilder setSpanNameExtractor(
      Function<SpanNameExtractor<Request>, ? extends SpanNameExtractor<? super Request>>
          spanNameExtractorTransformer) {
    serverBuilder.setSpanNameExtractor(spanNameExtractorTransformer);
    return this;
  }

  /**
   * Returns a new {@link RestletTelemetry} with the settings of this {@link
   * RestletTelemetryBuilder}.
   */
  public RestletTelemetry build() {
    return new RestletTelemetry(serverBuilder.instrumenter());
  }
}
