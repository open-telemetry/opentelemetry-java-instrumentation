/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v12_0;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpClientInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.jetty.httpclient.v12_0.internal.Experimental;
import io.opentelemetry.instrumentation.jetty.httpclient.v12_0.internal.JettyHttpClientInstrumenterBuilderFactory;
import java.util.Collection;
import java.util.function.UnaryOperator;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.util.ssl.SslContextFactory;

public final class JettyClientTelemetryBuilder {

  private final DefaultHttpClientInstrumenterBuilder<Request, Response> builder;
  private HttpClientTransport httpClientTransport;
  private SslContextFactory.Client sslContextFactory;

  static {
    Experimental.internalSetEmitExperimentalTelemetry(
        (builder, emit) -> builder.builder.setEmitExperimentalHttpClientTelemetry(emit));
  }

  JettyClientTelemetryBuilder(OpenTelemetry openTelemetry) {
    builder = JettyHttpClientInstrumenterBuilderFactory.create(openTelemetry);
  }

  /**
   * @deprecated Use {@link JettyClientTelemetry#newHttpClient(HttpClientTransport)} instead.
   */
  @Deprecated
  @CanIgnoreReturnValue
  public JettyClientTelemetryBuilder setHttpClientTransport(
      HttpClientTransport httpClientTransport) {
    this.httpClientTransport = httpClientTransport;
    return this;
  }

  /**
   * @deprecated Use {@link
   *     org.eclipse.jetty.client.HttpClient#setSslContextFactory(SslContextFactory.Client)} on the
   *     HttpClient returned by {@link JettyClientTelemetry#newHttpClient()} or {@link
   *     JettyClientTelemetry#newHttpClient(HttpClientTransport)} instead.
   */
  @Deprecated
  @CanIgnoreReturnValue
  public JettyClientTelemetryBuilder setSslContextFactory(
      SslContextFactory.Client sslContextFactory) {
    this.sslContextFactory = sslContextFactory;
    return this;
  }

  /**
   * Adds an {@link AttributesExtractor} to extract attributes from requests and responses. Executed
   * after all default extractors.
   */
  @CanIgnoreReturnValue
  public JettyClientTelemetryBuilder addAttributesExtractor(
      AttributesExtractor<Request, Response> attributesExtractor) {
    builder.addAttributesExtractor(attributesExtractor);
    return this;
  }

  /**
   * Configures HTTP request headers to capture as span attributes.
   *
   * @param requestHeaders HTTP header names to capture.
   */
  @CanIgnoreReturnValue
  public JettyClientTelemetryBuilder setCapturedRequestHeaders(Collection<String> requestHeaders) {
    builder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures HTTP response headers to capture as span attributes.
   *
   * @param responseHeaders HTTP header names to capture.
   */
  @CanIgnoreReturnValue
  public JettyClientTelemetryBuilder setCapturedResponseHeaders(
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
   * @see HttpClientAttributesExtractorBuilder#setKnownMethods(Collection)
   */
  @CanIgnoreReturnValue
  public JettyClientTelemetryBuilder setKnownMethods(Collection<String> knownMethods) {
    builder.setKnownMethods(knownMethods);
    return this;
  }

  /** Customizes the {@link SpanNameExtractor} by transforming the default instance. */
  @CanIgnoreReturnValue
  public JettyClientTelemetryBuilder setSpanNameExtractorCustomizer(
      UnaryOperator<SpanNameExtractor<Request>> spanNameExtractorCustomizer) {
    builder.setSpanNameExtractorCustomizer(spanNameExtractorCustomizer);
    return this;
  }

  /** Returns a new instance with the configured settings. */
  public JettyClientTelemetry build() {
    var instrumenter = builder.build();
    TracingHttpClient tracingHttpClient =
        TracingHttpClient.buildNew(instrumenter, sslContextFactory, httpClientTransport);

    return new JettyClientTelemetry(tracingHttpClient, instrumenter);
  }
}
