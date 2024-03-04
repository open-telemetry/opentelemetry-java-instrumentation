/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactornetty.v1_0;

import static io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor.alwaysClient;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpClientExperimentalMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpExperimentalAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractorBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanStatusExtractor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import reactor.netty.http.client.HttpClientRequest;
import reactor.netty.http.client.HttpClientResponse;

/** A builder of {@link ReactorNettyTelemetry}. */
public final class ReactorNettyTelemetryBuilder {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.reactor-netty-1.0";

  private final List<AttributesExtractor<HttpClientRequest, HttpClientResponse>>
      clientAdditionalExtractors = new ArrayList<>();

  private final OpenTelemetry openTelemetry;

  private boolean emitExperimentalHttpClientTelemetry = false;

  private Consumer<HttpClientAttributesExtractorBuilder<HttpClientRequest, HttpClientResponse>>
      clientExtractorConfigurer = builder -> {};
  private Consumer<HttpSpanNameExtractorBuilder<HttpClientRequest>> clientSpanNameExtractorConfigurer =
      builder -> {};
  
  private final HttpClientAttributesExtractorBuilder<HttpClientRequest, HttpClientResponse>
      httpClientAttributesExtractorBuilder = HttpClientAttributesExtractor.builder(ReactorNettyHttpClientAttributesGetter.INSTANCE);

  public ReactorNettyTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items for HttpClient.
   */
  @CanIgnoreReturnValue
  public ReactorNettyTelemetryBuilder addClientAttributesExtractor(
      AttributesExtractor<HttpClientRequest, HttpClientResponse> attributesExtractor) {
    clientAdditionalExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Configures the HTTP HttpClient request headers that will be captured as span attributes.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public ReactorNettyTelemetryBuilder setCapturedClientRequestHeaders(
      List<String> requestHeaders) {
    clientExtractorConfigurer =
        clientExtractorConfigurer.andThen(
            builder -> builder.setCapturedRequestHeaders(requestHeaders));
    return this;
  }

  /**
   * Configures the HTTP HttpClient response headers that will be captured as span attributes.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public ReactorNettyTelemetryBuilder setCapturedClientResponseHeaders(
      List<String> responseHeaders) {
    clientExtractorConfigurer =
        clientExtractorConfigurer.andThen(
            builder -> builder.setCapturedResponseHeaders(responseHeaders));
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
  public ReactorNettyTelemetryBuilder setKnownMethods(Set<String> knownMethods) {
    clientExtractorConfigurer =
        clientExtractorConfigurer.andThen(builder -> builder.setKnownMethods(knownMethods));
    clientSpanNameExtractorConfigurer =
        clientSpanNameExtractorConfigurer.andThen(builder -> builder.setKnownMethods(knownMethods));
    return this;
  }

  /**
   * Configures the instrumentation to emit experimental HTTP client metrics.
   *
   * @param emitExperimentalHttpClientTelemetry {@code true} if the experimental HTTP client metrics
   *     are to be emitted.
   */
  @CanIgnoreReturnValue
  public ReactorNettyTelemetryBuilder setEmitExperimentalHttpClientTelemetry(
      boolean emitExperimentalHttpClientTelemetry) {
    this.emitExperimentalHttpClientTelemetry = emitExperimentalHttpClientTelemetry;
    return this;
  }

  /**
   * Returns a new {@link ReactorNettyTelemetry} with the settings of this {@link
   * ReactorNettyTelemetryBuilder}.
   */
  public ReactorNettyTelemetry build() {
    ReactorNettyHttpClientAttributesGetter httpAttributesGetter = ReactorNettyHttpClientAttributesGetter.INSTANCE;

    HttpClientAttributesExtractorBuilder<HttpClientRequest, HttpClientResponse> extractorBuilder =
        HttpClientAttributesExtractor.builder(httpAttributesGetter);

    HttpSpanNameExtractorBuilder<HttpClientRequest> httpSpanNameExtractorBuilder =
        HttpSpanNameExtractor.builder(httpAttributesGetter);

    InstrumenterBuilder<HttpClientRequest, HttpClientResponse> clientBuilder =
        Instrumenter.<HttpClientRequest, HttpClientResponse>builder(
                openTelemetry, INSTRUMENTATION_NAME, httpSpanNameExtractorBuilder.build())
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(extractorBuilder.build())
            .addOperationMetrics(HttpClientMetrics.get());

    if (emitExperimentalHttpClientTelemetry) {
      clientBuilder
          .addAttributesExtractor(HttpExperimentalAttributesExtractor.create(httpAttributesGetter))
          .addOperationMetrics(HttpClientExperimentalMetrics.get());
    }

    // headers are injected elsewhere; ClientRequest is immutable
    return new ReactorNettyTelemetry(new InstrumentationContexts(clientBuilder.buildInstrumenter(alwaysClient())), openTelemetry.getPropagators());
  }
}
