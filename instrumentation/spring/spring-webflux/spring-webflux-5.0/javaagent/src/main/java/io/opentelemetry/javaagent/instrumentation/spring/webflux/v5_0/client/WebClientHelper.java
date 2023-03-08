/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0.client;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.net.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.internal.SpringWebfluxTelemetryClientBuilder;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.internal.WebClientNetAttributesGetter;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.internal.WebClientTracingFilter;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;
import java.util.List;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;

public final class WebClientHelper {

  private static final Instrumenter<ClientRequest, ClientResponse> instrumenter =
      new SpringWebfluxTelemetryClientBuilder(GlobalOpenTelemetry.get())
          .setCapturedClientRequestHeaders(CommonConfig.get().getClientRequestHeaders())
          .setCapturedClientResponseHeaders(CommonConfig.get().getClientResponseHeaders())
          .addClientAttributesExtractor(
              PeerServiceAttributesExtractor.create(
                  new WebClientNetAttributesGetter(), CommonConfig.get().getPeerServiceMapping()))
          .setCaptureExperimentalSpanAttributes(
              InstrumentationConfig.get()
                  .getBoolean(
                      "otel.instrumentation.spring-webflux.experimental-span-attributes", false))
          .build();

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
