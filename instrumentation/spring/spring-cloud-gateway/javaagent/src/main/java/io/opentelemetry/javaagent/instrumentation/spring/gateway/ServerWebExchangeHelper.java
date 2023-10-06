/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.gateway;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.web.server.ServerWebExchange;

public final class ServerWebExchangeHelper {

  /** Route info key. */
  public static final String ROUTE_INFO_ATTRIBUTES = "ROUTE_INFO";

  private ServerWebExchangeHelper() {}

  public static void extractAttributes(ServerWebExchange exchange, Context context) {
    // Record route info
    Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
    if (route != null) {
      Span currentSpan = Span.fromContext(context);
      assert currentSpan != null;
      currentSpan.setAttribute(ROUTE_INFO_ATTRIBUTES, route.toString());
    }
  }

  public static String extractServerRoute(ServerWebExchange exchange) {
    Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
    if (route != null) {
      return "Route@" + route.getId();
    }
    return null;
  }
}
