/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.internal.WebClientTracingFilter;
import java.util.List;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;

/** Entrypoint for instrumenting Spring Webflux HTTP clients. */
public final class SpringWebfluxClientTelemetry {

  /**
   * Returns a new {@link SpringWebfluxClientTelemetry} configured with the given {@link
   * OpenTelemetry}.
   */
  public static SpringWebfluxClientTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /**
   * Returns a new {@link SpringWebfluxClientTelemetryBuilder} configured with the given {@link
   * OpenTelemetry}.
   */
  public static SpringWebfluxClientTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new SpringWebfluxClientTelemetryBuilder(openTelemetry);
  }

  private final Instrumenter<ClientRequest, ClientResponse> clientInstrumenter;
  private final ContextPropagators propagators;

  SpringWebfluxClientTelemetry(
      Instrumenter<ClientRequest, ClientResponse> clientInstrumenter,
      ContextPropagators propagators) {
    this.clientInstrumenter = clientInstrumenter;
    this.propagators = propagators;
  }

  /**
   * Adds the OpenTelemetry telemetry producing {@link ExchangeFilterFunction} to the provided list
   * of filter functions.
   *
   * @param exchangeFilterFunctions existing filter functions
   */
  public void addFilter(List<ExchangeFilterFunction> exchangeFilterFunctions) {
    for (ExchangeFilterFunction filterFunction : exchangeFilterFunctions) {
      if (filterFunction instanceof WebClientTracingFilter) {
        return;
      }
    }
    exchangeFilterFunctions.add(new WebClientTracingFilter(clientInstrumenter, propagators));
  }

  /**
   * Adds the OpenTelemetry telemetry producing {@link ExchangeFilterFunction} to the provided list
   * of filter functions. Also registers the Reactor context propagation hook {@link
   * ContextPropagationOperator} for propagating OpenTelemetry context into reactive pipelines.
   *
   * @param exchangeFilterFunctions existing filter functions
   */
  public void addFilterAndRegisterReactorHook(
      List<ExchangeFilterFunction> exchangeFilterFunctions) {
    registerReactorHook();
    addFilter(exchangeFilterFunctions);
  }

  private static void registerReactorHook() {
    ContextPropagationOperator.builder().build().registerOnEachOperator();
  }
}
