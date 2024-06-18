/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.HttpClientConfigBuilder;
import io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal.HttpHeaderSetter;
import io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal.JettyClientHttpAttributesGetter;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.util.ssl.SslContextFactory;

/** A builder of {@link JettyClientTelemetry}. */
public final class JettyClientTelemetryBuilder extends HttpClientConfigBuilder<JettyClientTelemetryBuilder, Request, Response> {

  private HttpClientTransport httpClientTransport;
  private SslContextFactory sslContextFactory;

  JettyClientTelemetryBuilder(OpenTelemetry openTelemetry) {
    super(instrumentationName, openTelemetry, JettyClientHttpAttributesGetter.INSTANCE);
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
   * Returns a new {@link JettyClientTelemetry} with the settings of this {@link
   * JettyClientTelemetryBuilder}.
   */
  public JettyClientTelemetry build() {
    TracingHttpClient tracingHttpClient =
        TracingHttpClient.buildNew( instrumenterBuilder().buildClientInstrumenter(HttpHeaderSetter.INSTANCE) ,
            sslContextFactory,
            httpClientTransport);

    return new JettyClientTelemetry(tracingHttpClient);
  }
}
