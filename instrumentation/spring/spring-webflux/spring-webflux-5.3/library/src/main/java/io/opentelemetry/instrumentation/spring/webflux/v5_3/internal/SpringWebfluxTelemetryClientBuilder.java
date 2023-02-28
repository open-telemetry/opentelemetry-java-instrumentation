/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3.internal;

import static io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor.alwaysClient;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.SpringWebfluxTelemetry;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
// client builder is separate so that it can be used by javaagent instrumentation
// which supports 5.0, without triggering the server instrumentation which depends on webflux 5.3
public final class SpringWebfluxTelemetryClientBuilder {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-webflux-5.3";

  private final OpenTelemetry openTelemetry;
  private final List<AttributesExtractor<ClientRequest, ClientResponse>>
      clientAdditionalExtractors = new ArrayList<>();
  private final HttpClientAttributesExtractorBuilder<ClientRequest, ClientResponse>
      httpClientAttributesExtractorBuilder =
          HttpClientAttributesExtractor.builder(
              WebClientHttpAttributesGetter.INSTANCE, new WebClientNetAttributesGetter());

  private boolean captureExperimentalSpanAttributes = false;

  public SpringWebfluxTelemetryClientBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items for WebClient.
   */
  @CanIgnoreReturnValue
  public SpringWebfluxTelemetryClientBuilder addClientAttributesExtractor(
      AttributesExtractor<ClientRequest, ClientResponse> attributesExtractor) {
    clientAdditionalExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Configures the HTTP WebClient request headers that will be captured as span attributes.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public SpringWebfluxTelemetryClientBuilder setCapturedClientRequestHeaders(
      List<String> requestHeaders) {
    httpClientAttributesExtractorBuilder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures the HTTP WebClient response headers that will be captured as span attributes.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public SpringWebfluxTelemetryClientBuilder setCapturedClientResponseHeaders(
      List<String> responseHeaders) {
    httpClientAttributesExtractorBuilder.setCapturedResponseHeaders(responseHeaders);
    return this;
  }

  /**
   * Sets whether experimental attributes should be set to spans. These attributes may be changed or
   * removed in the future, so only enable this if you know you do not require attributes filled by
   * this instrumentation to be stable across versions.
   */
  @CanIgnoreReturnValue
  public SpringWebfluxTelemetryClientBuilder setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    return this;
  }

  /**
   * Returns a new {@link SpringWebfluxTelemetry} with the settings of this {@link
   * SpringWebfluxTelemetryClientBuilder}.
   */
  public Instrumenter<ClientRequest, ClientResponse> build() {
    WebClientHttpAttributesGetter httpClientAttributesGetter =
        WebClientHttpAttributesGetter.INSTANCE;

    InstrumenterBuilder<ClientRequest, ClientResponse> clientBuilder =
        Instrumenter.<ClientRequest, ClientResponse>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                HttpSpanNameExtractor.create(httpClientAttributesGetter))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpClientAttributesGetter))
            .addAttributesExtractor(httpClientAttributesExtractorBuilder.build())
            .addAttributesExtractors(clientAdditionalExtractors)
            .addOperationMetrics(HttpClientMetrics.get());

    if (captureExperimentalSpanAttributes) {
      clientBuilder.addAttributesExtractor(new WebClientExperimentalAttributesExtractor());
    }

    // headers are injected elsewhere; ClientRequest is immutable
    return clientBuilder.buildInstrumenter(alwaysClient());
  }
}
