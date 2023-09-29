/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0.client;

import static java.util.Collections.singletonList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientPeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.internal.ClientInstrumenterFactory;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.internal.WebClientHttpAttributesGetter;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.internal.WebClientTracingFilter;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;
import java.util.List;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;

public final class WebClientHelper {

  private static final Instrumenter<ClientRequest, ClientResponse> instrumenter =
      ClientInstrumenterFactory.create(
          GlobalOpenTelemetry.get(),
          builder ->
              builder
                  .setCapturedRequestHeaders(CommonConfig.get().getClientRequestHeaders())
                  .setCapturedResponseHeaders(CommonConfig.get().getClientResponseHeaders())
                  .setKnownMethods(CommonConfig.get().getKnownHttpRequestMethods()),
          builder -> builder.setKnownMethods(CommonConfig.get().getKnownHttpRequestMethods()),
          singletonList(
              HttpClientPeerServiceAttributesExtractor.create(
                  WebClientHttpAttributesGetter.INSTANCE,
                  CommonConfig.get().getPeerServiceResolver())),
          InstrumentationConfig.get()
              .getBoolean(
                  "otel.instrumentation.spring-webflux.experimental-span-attributes", false),
          CommonConfig.get().shouldEmitExperimentalHttpClientMetrics());

  public static void addFilter(List<ExchangeFilterFunction> exchangeFilterFunctions) {
    for (ExchangeFilterFunction filterFunction : exchangeFilterFunctions) {
      if (filterFunction instanceof WebClientTracingFilter) {
        return;
      }
    }
    exchangeFilterFunctions.add(
        new WebClientTracingFilter(instrumenter, GlobalOpenTelemetry.get().getPropagators()));
  }

  private WebClientHelper() {}
}
