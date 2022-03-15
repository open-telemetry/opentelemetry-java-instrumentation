/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.client;

import static io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor.alwaysClient;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;

/** A builder of {@link SpringWebfluxTracing}. */
public final class SpringWebfluxTracingBuilder {

  private final OpenTelemetry openTelemetry;
  private final List<AttributesExtractor<ClientRequest, ClientResponse>> additionalExtractors =
      new ArrayList<>();
  private final HttpClientAttributesExtractorBuilder<ClientRequest, ClientResponse>
      httpAttributesExtractorBuilder =
          HttpClientAttributesExtractor.builder(SpringWebfluxHttpAttributesGetter.INSTANCE);

  SpringWebfluxTracingBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items.
   */
  public SpringWebfluxTracingBuilder addAttributesExtractor(
      AttributesExtractor<ClientRequest, ClientResponse> attributesExtractor) {
    additionalExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Configures the HTTP request headers that will be captured as span attributes.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  public SpringWebfluxTracingBuilder setCapturedRequestHeaders(List<String> requestHeaders) {
    httpAttributesExtractorBuilder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures the HTTP response headers that will be captured as span attributes.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  public SpringWebfluxTracingBuilder setCapturedResponseHeaders(List<String> responseHeaders) {
    httpAttributesExtractorBuilder.setCapturedResponseHeaders(responseHeaders);
    return this;
  }

  /**
   * Returns a new {@link SpringWebfluxTracing} with the settings of this {@link
   * SpringWebfluxTracingBuilder}.
   */
  public SpringWebfluxTracing build() {
    SpringWebfluxHttpAttributesGetter httpAttributesGetter =
        SpringWebfluxHttpAttributesGetter.INSTANCE;
    SpringWebfluxNetAttributesGetter attributesGetter = new SpringWebfluxNetAttributesGetter();
    NetClientAttributesExtractor<ClientRequest, ClientResponse> attributesExtractor =
        NetClientAttributesExtractor.create(attributesGetter);

    InstrumenterBuilder<ClientRequest, ClientResponse> builder =
        Instrumenter.<ClientRequest, ClientResponse>builder(
                openTelemetry,
                "io.opentelemetry.spring-webflux-5.0",
                HttpSpanNameExtractor.create(httpAttributesGetter))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(httpAttributesExtractorBuilder.build())
            .addAttributesExtractor(attributesExtractor)
            .addAttributesExtractor(PeerServiceAttributesExtractor.create(attributesGetter))
            .addAttributesExtractors(additionalExtractors)
            .addRequestMetrics(HttpClientMetrics.get());

    if (SpringWebfluxExperimentalAttributesExtractor.enabled()) {
      builder.addAttributesExtractor(new SpringWebfluxExperimentalAttributesExtractor());
    }

    // headers are injected elsewhere; ClientRequest is immutable
    Instrumenter<ClientRequest, ClientResponse> instrumenter =
        builder.newInstrumenter(alwaysClient());

    return new SpringWebfluxTracing(instrumenter, openTelemetry.getPropagators());
  }
}
