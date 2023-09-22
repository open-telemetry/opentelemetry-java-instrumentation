/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractorBuilder;
import io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal.JettyClientInstrumenterFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.util.ssl.SslContextFactory;

/** A builder of {@link JettyClientTelemetry}. */
public final class JettyClientTelemetryBuilder {

  private final OpenTelemetry openTelemetry;
  private final List<AttributesExtractor<? super Request, ? super Response>> additionalExtractors =
      new ArrayList<>();
  private Consumer<HttpClientAttributesExtractorBuilder<Request, Response>> extractorConfigurer =
      builder -> {};
  private Consumer<HttpSpanNameExtractorBuilder<Request>> spanNameExtractorConfigurer =
      builder -> {};
  private boolean emitExperimentalHttpClientMetrics = false;
  private HttpClientTransport httpClientTransport;
  private SslContextFactory sslContextFactory;

  JettyClientTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  @CanIgnoreReturnValue
  public JettyClientTelemetryBuilder setHttpClientTransport(
      HttpClientTransport httpClientTransport) {
    this.httpClientTransport = httpClientTransport;
    return this;
  }

  @CanIgnoreReturnValue
  public JettyClientTelemetryBuilder setSslContextFactory(SslContextFactory sslContextFactory) {
    this.sslContextFactory = sslContextFactory;
    return this;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items.
   */
  @CanIgnoreReturnValue
  public JettyClientTelemetryBuilder addAttributeExtractor(
      AttributesExtractor<? super Request, ? super Response> attributesExtractor) {
    additionalExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Configures the HTTP request headers that will be captured as span attributes.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public JettyClientTelemetryBuilder setCapturedRequestHeaders(List<String> requestHeaders) {
    extractorConfigurer =
        extractorConfigurer.andThen(builder -> builder.setCapturedRequestHeaders(requestHeaders));
    return this;
  }

  /**
   * Configures the HTTP response headers that will be captured as span attributes.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public JettyClientTelemetryBuilder setCapturedResponseHeaders(List<String> responseHeaders) {
    extractorConfigurer =
        extractorConfigurer.andThen(builder -> builder.setCapturedResponseHeaders(responseHeaders));
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
   */
  @CanIgnoreReturnValue
  public JettyClientTelemetryBuilder setKnownMethods(Set<String> knownMethods) {
    extractorConfigurer =
        extractorConfigurer.andThen(builder -> builder.setKnownMethods(knownMethods));
    spanNameExtractorConfigurer =
        spanNameExtractorConfigurer.andThen(builder -> builder.setKnownMethods(knownMethods));
    return this;
  }

  /**
   * Configures the instrumentation to emit experimental HTTP client metrics.
   *
   * @param emitExperimentalHttpClientMetrics {@code true} if the experimental HTTP client metrics
   *     are to be emitted.
   */
  @CanIgnoreReturnValue
  public JettyClientTelemetryBuilder setEmitExperimentalHttpClientMetrics(
      boolean emitExperimentalHttpClientMetrics) {
    this.emitExperimentalHttpClientMetrics = emitExperimentalHttpClientMetrics;
    return this;
  }

  /**
   * Returns a new {@link JettyClientTelemetry} with the settings of this {@link
   * JettyClientTelemetryBuilder}.
   */
  public JettyClientTelemetry build() {
    TracingHttpClient tracingHttpClient =
        TracingHttpClient.buildNew(
            JettyClientInstrumenterFactory.create(
                openTelemetry,
                extractorConfigurer,
                spanNameExtractorConfigurer,
                additionalExtractors,
                emitExperimentalHttpClientMetrics),
            sslContextFactory,
            httpClientTransport);

    return new JettyClientTelemetry(tracingHttpClient);
  }
}
