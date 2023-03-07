/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.internal.SpringWebfluxTelemetryClientBuilder;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.server.ServerWebExchange;

/** A builder of {@link SpringWebfluxTelemetry}. */
public final class SpringWebfluxTelemetryBuilder {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-webflux-5.3";

  private final OpenTelemetry openTelemetry;

  private final SpringWebfluxTelemetryClientBuilder clientBuilder;

  private final List<AttributesExtractor<ServerWebExchange, ServerWebExchange>>
      serverAdditionalExtractors = new ArrayList<>();
  private final HttpServerAttributesExtractorBuilder<ServerWebExchange, ServerWebExchange>
      httpServerAttributesExtractorBuilder =
          HttpServerAttributesExtractor.builder(
              WebfluxServerHttpAttributesGetter.INSTANCE,
              WebfluxServerNetAttributesGetter.INSTANCE);

  SpringWebfluxTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
    clientBuilder = new SpringWebfluxTelemetryClientBuilder(openTelemetry);
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items for WebClient.
   */
  @CanIgnoreReturnValue
  public SpringWebfluxTelemetryBuilder addClientAttributesExtractor(
      AttributesExtractor<ClientRequest, ClientResponse> attributesExtractor) {
    clientBuilder.addClientAttributesExtractor(attributesExtractor);
    return this;
  }

  /**
   * Configures the HTTP WebClient request headers that will be captured as span attributes.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public SpringWebfluxTelemetryBuilder setCapturedClientRequestHeaders(
      List<String> requestHeaders) {
    clientBuilder.setCapturedClientRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures the HTTP WebClient response headers that will be captured as span attributes.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public SpringWebfluxTelemetryBuilder setCapturedClientResponseHeaders(
      List<String> responseHeaders) {
    clientBuilder.setCapturedClientResponseHeaders(responseHeaders);
    return this;
  }

  /**
   * Sets whether experimental attributes should be set to spans. These attributes may be changed or
   * removed in the future, so only enable this if you know you do not require attributes filled by
   * this instrumentation to be stable across versions.
   */
  @CanIgnoreReturnValue
  public SpringWebfluxTelemetryBuilder setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    clientBuilder.setCaptureExperimentalSpanAttributes(captureExperimentalSpanAttributes);
    return this;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items.
   */
  @CanIgnoreReturnValue
  public SpringWebfluxTelemetryBuilder addServerAttributesExtractor(
      AttributesExtractor<ServerWebExchange, ServerWebExchange> attributesExtractor) {
    serverAdditionalExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Configures the HTTP request headers that will be captured as span attributes from server
   * instrumentation.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public SpringWebfluxTelemetryBuilder setCapturedServerRequestHeaders(
      List<String> requestHeaders) {
    httpServerAttributesExtractorBuilder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures the HTTP response headers that will be captured as span attributes from server
   * instrumentation.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public SpringWebfluxTelemetryBuilder setCapturedServerResponseHeaders(
      List<String> responseHeaders) {
    httpServerAttributesExtractorBuilder.setCapturedResponseHeaders(responseHeaders);
    return this;
  }

  /**
   * Returns a new {@link SpringWebfluxTelemetry} with the settings of this {@link
   * SpringWebfluxTelemetryBuilder}.
   */
  public SpringWebfluxTelemetry build() {

    // headers are injected elsewhere; ClientRequest is immutable
    Instrumenter<ClientRequest, ClientResponse> clientInstrumenter = clientBuilder.build();

    WebfluxServerHttpAttributesGetter serverAttributesGetter =
        WebfluxServerHttpAttributesGetter.INSTANCE;
    SpanNameExtractor<ServerWebExchange> serverSpanNameExtractor =
        HttpSpanNameExtractor.create(serverAttributesGetter);

    Instrumenter<ServerWebExchange, ServerWebExchange> serverInstrumenter =
        Instrumenter.<ServerWebExchange, ServerWebExchange>builder(
                openTelemetry, INSTRUMENTATION_NAME, serverSpanNameExtractor)
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(serverAttributesGetter))
            .addAttributesExtractor(httpServerAttributesExtractorBuilder.build())
            .addAttributesExtractors(serverAdditionalExtractors)
            .addOperationMetrics(HttpServerMetrics.get())
            .buildServerInstrumenter(WebfluxTextMapGetter.INSTANCE);

    return new SpringWebfluxTelemetry(
        clientInstrumenter, serverInstrumenter, openTelemetry.getPropagators());
  }
}
