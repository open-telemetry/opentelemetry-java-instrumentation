/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3.internal;

import static io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor.alwaysClient;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientExperimentalMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractorBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
// client builder is separate so that it can be used by javaagent instrumentation
// which supports 5.0, without triggering the server instrumentation which depends on webflux 5.3
public final class ClientInstrumenterFactory {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-webflux-5.3";

  public static Instrumenter<ClientRequest, ClientResponse> create(
      OpenTelemetry openTelemetry,
      Consumer<HttpClientAttributesExtractorBuilder<ClientRequest, ClientResponse>>
          extractorConfigurer,
      Consumer<HttpSpanNameExtractorBuilder<ClientRequest>> spanNameExtractorConfigurer,
      List<AttributesExtractor<ClientRequest, ClientResponse>> additionalExtractors,
      boolean captureExperimentalSpanAttributes,
      boolean emitExperimentalHttpClientMetrics) {

    WebClientHttpAttributesGetter httpAttributesGetter = WebClientHttpAttributesGetter.INSTANCE;

    HttpClientAttributesExtractorBuilder<ClientRequest, ClientResponse> extractorBuilder =
        HttpClientAttributesExtractor.builder(httpAttributesGetter);
    extractorConfigurer.accept(extractorBuilder);

    HttpSpanNameExtractorBuilder<ClientRequest> httpSpanNameExtractorBuilder =
        HttpSpanNameExtractor.builder(httpAttributesGetter);
    spanNameExtractorConfigurer.accept(httpSpanNameExtractorBuilder);

    InstrumenterBuilder<ClientRequest, ClientResponse> clientBuilder =
        Instrumenter.<ClientRequest, ClientResponse>builder(
                openTelemetry, INSTRUMENTATION_NAME, httpSpanNameExtractorBuilder.build())
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(extractorBuilder.build())
            .addAttributesExtractors(additionalExtractors)
            .addOperationMetrics(HttpClientMetrics.get());

    if (captureExperimentalSpanAttributes) {
      clientBuilder.addAttributesExtractor(new WebClientExperimentalAttributesExtractor());
    }
    if (emitExperimentalHttpClientMetrics) {
      clientBuilder.addOperationMetrics(HttpClientExperimentalMetrics.get());
    }

    // headers are injected elsewhere; ClientRequest is immutable
    return clientBuilder.buildInstrumenter(alwaysClient());
  }

  private ClientInstrumenterFactory() {}
}
