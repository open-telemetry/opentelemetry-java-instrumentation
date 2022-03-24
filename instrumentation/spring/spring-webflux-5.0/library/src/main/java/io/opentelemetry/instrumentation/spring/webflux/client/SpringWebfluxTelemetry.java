/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.client;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.List;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;

/** Entrypoint for instrumenting Spring Webflux HTTP clients. */
public final class SpringWebfluxTelemetry {

  /**
   * Returns a new {@link SpringWebfluxTelemetry} configured with the given {@link OpenTelemetry}.
   */
  public static SpringWebfluxTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /**
   * Returns a new {@link SpringWebfluxTelemetryBuilder} configured with the given {@link
   * OpenTelemetry}.
   */
  public static SpringWebfluxTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new SpringWebfluxTelemetryBuilder(openTelemetry);
  }

  private final Instrumenter<ClientRequest, ClientResponse> instrumenter;
  private final ContextPropagators propagators;

  SpringWebfluxTelemetry(
      Instrumenter<ClientRequest, ClientResponse> instrumenter, ContextPropagators propagators) {
    this.instrumenter = instrumenter;
    this.propagators = propagators;
  }

  public void addClientTracingFilter(List<ExchangeFilterFunction> exchangeFilterFunctions) {
    for (ExchangeFilterFunction filterFunction : exchangeFilterFunctions) {
      if (filterFunction instanceof WebClientTracingFilter) {
        return;
      }
    }
    exchangeFilterFunctions.add(0, new WebClientTracingFilter(instrumenter, propagators));
  }
}
