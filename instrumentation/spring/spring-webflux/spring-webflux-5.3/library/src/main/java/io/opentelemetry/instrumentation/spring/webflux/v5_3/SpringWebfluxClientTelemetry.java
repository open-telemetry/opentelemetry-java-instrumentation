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
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;

/** Entrypoint for instrumenting Spring Webflux HTTP clients. */
public final class SpringWebfluxClientTelemetry {
  private final Instrumenter<ClientRequest, ClientResponse> clientInstrumenter;
  private final ContextPropagators propagators;

  /** Returns a new instance configured with the given {@link OpenTelemetry} instance. */
  public static SpringWebfluxClientTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /** Returns a builder configured with the given {@link OpenTelemetry} instance. */
  public static SpringWebfluxClientTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new SpringWebfluxClientTelemetryBuilder(openTelemetry);
  }

  SpringWebfluxClientTelemetry(
      Instrumenter<ClientRequest, ClientResponse> clientInstrumenter,
      ContextPropagators propagators) {
    this.clientInstrumenter = clientInstrumenter;
    this.propagators = propagators;
  }

  /** Returns an instrumented {@link ExchangeFilterFunction}. */
  public ExchangeFilterFunction createFilter() {
    return new WebClientTracingFilter(clientInstrumenter, propagators);
  }

  /**
   * Returns an instrumented {@link ExchangeFilterFunction}. Also registers the Reactor context
   * propagation hook for reactive pipelines.
   */
  public ExchangeFilterFunction createFilterAndRegisterReactorHook() {
    registerReactorHook();
    return createFilter();
  }

  private static void registerReactorHook() {
    ContextPropagationOperator.builder().build().registerOnEachOperator();
  }
}
