/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.DefaultHttpClientTelemetryBuilder;
import io.opentelemetry.instrumentation.api.incubator.builder.HttpClientTelemetryBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal.HttpHeaderSetter;
import io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal.JettyClientHttpAttributesGetter;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.util.ssl.SslContextFactory;

/** A builder of {@link JettyClientTelemetry}. */
public final class JettyClientTelemetryBuilder
    implements HttpClientTelemetryBuilder<JettyClientTelemetryBuilder, Request, Response> {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jetty-httpclient-9.2";
  private final DefaultHttpClientTelemetryBuilder<Request, Response> builder;
  private HttpClientTransport httpClientTransport;
  private SslContextFactory sslContextFactory;

  JettyClientTelemetryBuilder(OpenTelemetry openTelemetry) {
    builder =
        new DefaultHttpClientTelemetryBuilder<>(
            INSTRUMENTATION_NAME,
            openTelemetry,
            JettyClientHttpAttributesGetter.INSTANCE,
            Optional.of(HttpHeaderSetter.INSTANCE));
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

  @Override
  @CanIgnoreReturnValue
  public JettyClientTelemetryBuilder addAttributeExtractor(
      AttributesExtractor<? super Request, ? super Response> attributesExtractor) {
    builder.addAttributeExtractor(attributesExtractor);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public JettyClientTelemetryBuilder setCapturedRequestHeaders(List<String> requestHeaders) {
    builder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public JettyClientTelemetryBuilder setCapturedResponseHeaders(List<String> responseHeaders) {
    builder.setCapturedResponseHeaders(responseHeaders);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public JettyClientTelemetryBuilder setKnownMethods(Set<String> knownMethods) {
    builder.setKnownMethods(knownMethods);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public JettyClientTelemetryBuilder setEmitExperimentalHttpClientMetrics(
      boolean emitExperimentalHttpClientMetrics) {
    builder.setEmitExperimentalHttpClientMetrics(emitExperimentalHttpClientMetrics);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public JettyClientTelemetryBuilder setSpanNameExtractor(
      Function<SpanNameExtractor<Request>, ? extends SpanNameExtractor<? super Request>>
          spanNameExtractorTransformer) {
    builder.setSpanNameExtractor(spanNameExtractorTransformer);
    return this;
  }

  /**
   * Returns a new {@link JettyClientTelemetry} with the settings of this {@link
   * JettyClientTelemetryBuilder}.
   */
  public JettyClientTelemetry build() {
    TracingHttpClient tracingHttpClient =
        TracingHttpClient.buildNew(builder.instrumenter(), sslContextFactory, httpClientTransport);

    return new JettyClientTelemetry(tracingHttpClient);
  }
}
