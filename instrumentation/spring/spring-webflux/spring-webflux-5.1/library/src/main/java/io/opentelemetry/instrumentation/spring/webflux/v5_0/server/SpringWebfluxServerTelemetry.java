/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_0.server;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import org.springframework.web.server.ServerWebExchange;

public final class SpringWebfluxServerTelemetry {
  // We use ServerWebExchange (which holds both the request and response)
  // because we need it to get the HTTP route while instrumenting.
  private final Instrumenter<ServerWebExchange, ServerWebExchange> instrumenter;

  public static SpringWebfluxServerTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  public static SpringWebfluxTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new SpringWebfluxTelemetryBuilder(openTelemetry);
  }

  private static void registerReactorHook() {
    ContextPropagationOperator.builder().build().registerOnEachOperator();
  }

  SpringWebfluxServerTelemetry(Instrumenter<ServerWebExchange, ServerWebExchange> instrumenter) {
    this.instrumenter = instrumenter;
  }

  public TelemetryProducingWebFilter createWebFilter() {
    return new TelemetryProducingWebFilter(instrumenter);
  }

  public TelemetryProducingWebFilter createWebFilterAndRegisterReactorHook() {
    registerReactorHook();
    return this.createWebFilter();
  }
}
