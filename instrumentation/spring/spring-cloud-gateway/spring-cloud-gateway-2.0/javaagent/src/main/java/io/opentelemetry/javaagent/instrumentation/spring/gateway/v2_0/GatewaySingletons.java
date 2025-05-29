/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.gateway.v2_0;

import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteGetter;
import org.springframework.web.server.ServerWebExchange;

public final class GatewaySingletons {

  private GatewaySingletons() {}

  public static HttpServerRouteGetter<ServerWebExchange> httpRouteGetter() {
    return (context, exchange) -> ServerWebExchangeHelper.extractServerRoute(exchange);
  }
}
