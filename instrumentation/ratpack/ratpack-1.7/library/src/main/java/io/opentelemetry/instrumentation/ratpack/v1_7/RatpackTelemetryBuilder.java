/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.v1_7;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpClientInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpServerInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesExtractorBuilder;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import ratpack.http.Request;
import ratpack.http.Response;
import ratpack.http.client.HttpResponse;
import ratpack.http.client.RequestSpec;

/** A builder for {@link RatpackTelemetry}. */
public final class RatpackTelemetryBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.ratpack-1.7";

  private final DefaultHttpClientInstrumenterBuilder<RequestSpec, HttpResponse> clientBuilder;
  private final DefaultHttpServerInstrumenterBuilder<Request, Response> serverBuilder;

  RatpackTelemetryBuilder(OpenTelemetry openTelemetry) {
    clientBuilder =
        DefaultHttpClientInstrumenterBuilder.create(
            INSTRUMENTATION_NAME,
            openTelemetry,
            RatpackHttpClientAttributesGetter.INSTANCE,
            RequestHeaderSetter.INSTANCE);
    serverBuilder =
        DefaultHttpServerInstrumenterBuilder.create(
            INSTRUMENTATION_NAME,
            openTelemetry,
            RatpackHttpAttributesGetter.INSTANCE,
            RatpackGetter.INSTANCE);
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items. The {@link AttributesExtractor} will be executed after all default extractors.
   */
  @CanIgnoreReturnValue
  public RatpackTelemetryBuilder addAttributeExtractor(
      AttributesExtractor<? super Request, ? super Response> attributesExtractor) {
    serverBuilder.addAttributesExtractor(attributesExtractor);
    return this;
  }

  @CanIgnoreReturnValue
  public RatpackTelemetryBuilder addClientAttributeExtractor(
      AttributesExtractor<? super RequestSpec, ? super HttpResponse> attributesExtractor) {
    clientBuilder.addAttributeExtractor(attributesExtractor);
    return this;
  }

  /**
   * Configures the HTTP server request headers that will be captured as span attributes.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public RatpackTelemetryBuilder setCapturedServerRequestHeaders(List<String> requestHeaders) {
    serverBuilder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures the HTTP server response headers that will be captured as span attributes.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public RatpackTelemetryBuilder setCapturedServerResponseHeaders(List<String> responseHeaders) {
    serverBuilder.setCapturedResponseHeaders(responseHeaders);
    return this;
  }

  /**
   * Configures the HTTP client request headers that will be captured as span attributes.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public RatpackTelemetryBuilder setCapturedClientRequestHeaders(List<String> requestHeaders) {
    clientBuilder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures the HTTP client response headers that will be captured as span attributes.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public RatpackTelemetryBuilder setCapturedClientResponseHeaders(List<String> responseHeaders) {
    clientBuilder.setCapturedResponseHeaders(responseHeaders);
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
   * @see HttpClientAttributesExtractorBuilder#setKnownMethods(Set)
   * @see HttpServerAttributesExtractorBuilder#setKnownMethods(Set)
   */
  @CanIgnoreReturnValue
  public RatpackTelemetryBuilder setKnownMethods(Set<String> knownMethods) {
    clientBuilder.setKnownMethods(knownMethods);
    serverBuilder.setKnownMethods(knownMethods);
    return this;
  }

  /**
   * Configures the instrumentation to emit experimental HTTP client metrics.
   *
   * @param emitExperimentalHttpClientMetrics {@code true} if the experimental HTTP client metrics
   *     are to be emitted.
   */
  @CanIgnoreReturnValue
  public RatpackTelemetryBuilder setEmitExperimentalHttpClientMetrics(
      boolean emitExperimentalHttpClientMetrics) {
    clientBuilder.setEmitExperimentalHttpClientMetrics(emitExperimentalHttpClientMetrics);
    return this;
  }

  /**
   * Configures the instrumentation to emit experimental HTTP server metrics.
   *
   * @param emitExperimentalHttpServerMetrics {@code true} if the experimental HTTP server metrics
   *     are to be emitted.
   */
  @CanIgnoreReturnValue
  public RatpackTelemetryBuilder setEmitExperimentalHttpServerMetrics(
      boolean emitExperimentalHttpServerMetrics) {
    serverBuilder.setEmitExperimentalHttpServerMetrics(emitExperimentalHttpServerMetrics);
    return this;
  }

  /** Sets custom client {@link SpanNameExtractor} via transform function. */
  @CanIgnoreReturnValue
  public RatpackTelemetryBuilder setClientSpanNameExtractor(
      Function<
              SpanNameExtractor<? super RequestSpec>,
              ? extends SpanNameExtractor<? super RequestSpec>>
          clientSpanNameExtractor) {
    clientBuilder.setSpanNameExtractor(clientSpanNameExtractor);
    return this;
  }

  /** Sets custom server {@link SpanNameExtractor} via transform function. */
  @CanIgnoreReturnValue
  public RatpackTelemetryBuilder setServerSpanNameExtractor(
      Function<SpanNameExtractor<? super Request>, ? extends SpanNameExtractor<? super Request>>
          serverSpanNameExtractor) {
    serverBuilder.setSpanNameExtractor(serverSpanNameExtractor);
    return this;
  }

  /** Returns a new {@link RatpackTelemetry} with the configuration of this builder. */
  public RatpackTelemetry build() {
    return new RatpackTelemetry(serverBuilder.build(), clientBuilder.build());
  }
}
