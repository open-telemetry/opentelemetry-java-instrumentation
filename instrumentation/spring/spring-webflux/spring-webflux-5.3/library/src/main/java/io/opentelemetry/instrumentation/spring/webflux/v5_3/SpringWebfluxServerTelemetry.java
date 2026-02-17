/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;

/** Entrypoint for instrumenting Spring Webflux HTTP services. */
public final class SpringWebfluxServerTelemetry {

  /** Returns a new instance configured with the given {@link OpenTelemetry} instance. */
  public static SpringWebfluxServerTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /** Returns a builder configured with the given {@link OpenTelemetry} instance. */
  public static SpringWebfluxServerTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new SpringWebfluxServerTelemetryBuilder(openTelemetry);
  }

  // We use ServerWebExchange (which holds both the request and response)
  // because we need it to get the HTTP route while instrumenting.
  private final Instrumenter<ServerWebExchange, ServerWebExchange> serverInstrumenter;

  SpringWebfluxServerTelemetry(
      Instrumenter<ServerWebExchange, ServerWebExchange> serverInstrumenter) {
    this.serverInstrumenter = serverInstrumenter;
  }

  /** Returns a {@link WebFilter} that instruments HTTP server requests. */
  public WebFilter createWebFilter() {
    return new TelemetryProducingWebFilter(serverInstrumenter);
  }

  /**
   * Returns a {@link WebFilter} that instruments HTTP server requests. Also registers the Reactor
   * context propagation hook for reactive pipelines.
   */
  public WebFilter createWebFilterAndRegisterReactorHook() {
    registerReactorHook();
    return this.createWebFilter();
  }

  private static void registerReactorHook() {
    ContextPropagationOperator.builder().build().registerOnEachOperator();
  }
}
