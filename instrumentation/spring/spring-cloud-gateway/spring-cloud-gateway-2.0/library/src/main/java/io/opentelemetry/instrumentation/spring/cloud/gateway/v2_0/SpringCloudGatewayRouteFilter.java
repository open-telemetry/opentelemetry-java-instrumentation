/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.cloud.gateway.v2_0;

import static io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource.NESTED_CONTROLLER;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.spring.cloud.gateway.common.internal.GatewayRouteIdConverger;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** A Spring Cloud Gateway filter that records the matched route on HTTP server telemetry. */
public final class SpringCloudGatewayRouteFilter implements GlobalFilter, Ordered {

  /** Creates a new route filter. */
  public SpringCloudGatewayRouteFilter() {}

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
    HttpServerRoute.update(
        Context.current(),
        NESTED_CONTROLLER,
        (context, matchedRoute) ->
            matchedRoute == null ? null : GatewayRouteIdConverger.converge(matchedRoute.getId()),
        route);
    return chain.filter(exchange);
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }
}
