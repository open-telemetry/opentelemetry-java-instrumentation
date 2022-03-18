/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal.JettyClientInstrumenterBuilder;
import java.util.List;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.util.ssl.SslContextFactory;

/** A builder of {@link JettyClientTelemetry}. */
public final class JettyClientTelemetryBuilder {

  private final JettyClientInstrumenterBuilder instrumenterBuilder;
  private HttpClientTransport httpClientTransport;
  private SslContextFactory sslContextFactory;

  JettyClientTelemetryBuilder(OpenTelemetry openTelemetry) {
    instrumenterBuilder = new JettyClientInstrumenterBuilder(openTelemetry);
  }

  public JettyClientTelemetryBuilder setHttpClientTransport(
      HttpClientTransport httpClientTransport) {
    this.httpClientTransport = httpClientTransport;
    return this;
  }

  public JettyClientTelemetryBuilder setSslContextFactory(SslContextFactory sslContextFactory) {
    this.sslContextFactory = sslContextFactory;
    return this;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items.
   */
  public JettyClientTelemetryBuilder addAttributeExtractor(
      AttributesExtractor<? super Request, ? super Response> attributesExtractor) {
    instrumenterBuilder.addAttributeExtractor(attributesExtractor);
    return this;
  }

  /**
   * Configures the HTTP request headers that will be captured as span attributes.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  public JettyClientTelemetryBuilder setCapturedRequestHeaders(List<String> requestHeaders) {
    instrumenterBuilder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures the HTTP response headers that will be captured as span attributes.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  public JettyClientTelemetryBuilder setCapturedResponseHeaders(List<String> responseHeaders) {
    instrumenterBuilder.setCapturedResponseHeaders(responseHeaders);
    return this;
  }

  /**
   * Returns a new {@link JettyClientTelemetry} with the settings of this {@link
   * JettyClientTelemetryBuilder}.
   */
  public JettyClientTelemetry build() {
    TracingHttpClient tracingHttpClient =
        TracingHttpClient.buildNew(
            instrumenterBuilder.build(), this.sslContextFactory, this.httpClientTransport);

    return new JettyClientTelemetry(tracingHttpClient);
  }
}
