/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.CapturedHttpHeaders;
import io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal.JettyClientInstrumenterBuilder;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.util.ssl.SslContextFactory;

/** A builder of {@link JettyClientTracing}. */
public final class JettyClientTracingBuilder {

  private final JettyClientInstrumenterBuilder instrumenterBuilder;
  private HttpClientTransport httpClientTransport;
  private SslContextFactory sslContextFactory;

  JettyClientTracingBuilder(OpenTelemetry openTelemetry) {
    instrumenterBuilder = new JettyClientInstrumenterBuilder(openTelemetry);
  }

  public JettyClientTracingBuilder setHttpClientTransport(HttpClientTransport httpClientTransport) {
    this.httpClientTransport = httpClientTransport;
    return this;
  }

  public JettyClientTracingBuilder setSslContextFactory(SslContextFactory sslContextFactory) {
    this.sslContextFactory = sslContextFactory;
    return this;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items.
   */
  public JettyClientTracingBuilder addAttributeExtractor(
      AttributesExtractor<? super Request, ? super Response> attributesExtractor) {
    instrumenterBuilder.addAttributeExtractor(attributesExtractor);
    return this;
  }

  /**
   * Configure the instrumentation to capture chosen HTTP request and response headers as span
   * attributes.
   *
   * @param capturedHttpHeaders An instance of {@link CapturedHttpHeaders} containing the configured
   *     HTTP request and response names.
   */
  public JettyClientTracingBuilder setCaptureHttpHeaders(CapturedHttpHeaders capturedHttpHeaders) {
    instrumenterBuilder.setCaptureHttpHeaders(capturedHttpHeaders);
    return this;
  }

  /**
   * Returns a new {@link JettyClientTracing} with the settings of this {@link
   * JettyClientTracingBuilder}.
   */
  public JettyClientTracing build() {
    TracingHttpClient tracingHttpClient =
        TracingHttpClient.buildNew(
            instrumenterBuilder.build(), this.sslContextFactory, this.httpClientTransport);

    return new JettyClientTracing(tracingHttpClient);
  }
}
