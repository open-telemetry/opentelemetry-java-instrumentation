/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.cloud.gateway.v2_0;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteGetter;
import io.opentelemetry.instrumentation.spring.cloud.gateway.common.internal.GatewayRouteIdConverger;
import javax.annotation.Nullable;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.web.server.ServerWebExchange;

/** Extracts the matched Spring Cloud Gateway route ID for HTTP server telemetry. */
public final class SpringCloudGatewayRouteGetter
    implements HttpServerRouteGetter<ServerWebExchange> {
  /** Creates a getter for Spring Cloud Gateway routes. */
  public SpringCloudGatewayRouteGetter() {}

  @Override
  @Nullable
  public String get(Context context, @Nullable ServerWebExchange exchange) {
    if (exchange == null) {
      return null;
    }
    Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
    return route == null ? null : GatewayRouteIdConverger.converge(route.getId());
  }
}
