/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0.client;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.internal.WebClientHttpAttributesGetter;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.internal.WebClientTracingFilter;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpClientInstrumenters;
import java.util.List;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;

public final class WebClientHelper {

  private static final Instrumenter<ClientRequest, ClientResponse> instrumenter =
      JavaagentHttpClientInstrumenters.create(
          "io.opentelemetry.spring-webflux-5.0", WebClientHttpAttributesGetter.INSTANCE);

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
