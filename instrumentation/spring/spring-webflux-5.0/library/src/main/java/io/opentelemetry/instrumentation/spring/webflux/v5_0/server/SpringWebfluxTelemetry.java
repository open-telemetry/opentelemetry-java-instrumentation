/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_0.server;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;

public final class SpringWebfluxServerTelemetry {
  private final Instrumenter<ServerHttpRequest, ServerHttpResponse> instrumenter;

  public static SpringWebfluxServerTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  public static SpringWebfluxTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new SpringWebfluxTelemetryBuilder(openTelemetry);
  }

  private static void registerReactorHook() {
    ContextPropagationOperator.builder().build().registerOnEachOperator();
  }

  SpringWebfluxServerTelemetry(Instrumenter<ServerHttpRequest, ServerHttpResponse> instrumenter) {
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
