/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.client;

import static io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor.alwaysClient;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.CapturedHttpHeaders;
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
  private CapturedHttpHeaders capturedHttpHeaders = CapturedHttpHeaders.client(Config.get());

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
   * Configure the instrumentation to capture chosen HTTP request and response headers as span
   * attributes.
   *
   * @param capturedHttpHeaders An instance of {@link CapturedHttpHeaders} containing the configured
   *     HTTP request and response names.
   */
  public SpringWebfluxTracingBuilder captureHttpHeaders(CapturedHttpHeaders capturedHttpHeaders) {
    this.capturedHttpHeaders = capturedHttpHeaders;
    return this;
  }

  /**
   * Returns a new {@link SpringWebfluxTracing} with the settings of this {@link
   * SpringWebfluxTracingBuilder}.
   */
  public SpringWebfluxTracing build() {
    SpringWebfluxHttpAttributesExtractor httpAttributesExtractor =
        new SpringWebfluxHttpAttributesExtractor(capturedHttpHeaders);
    SpringWebfluxNetAttributesAdapter attributesAdapter = new SpringWebfluxNetAttributesAdapter();
    NetClientAttributesExtractor<ClientRequest, ClientResponse> attributesExtractor =
        NetClientAttributesExtractor.create(attributesAdapter);

    InstrumenterBuilder<ClientRequest, ClientResponse> builder =
        Instrumenter.<ClientRequest, ClientResponse>builder(
                openTelemetry,
                "io.opentelemetry.spring-webflux-5.0",
                HttpSpanNameExtractor.create(httpAttributesExtractor))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesExtractor))
            .addAttributesExtractor(httpAttributesExtractor)
            .addAttributesExtractor(attributesExtractor)
            .addAttributesExtractor(PeerServiceAttributesExtractor.create(attributesAdapter))
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
