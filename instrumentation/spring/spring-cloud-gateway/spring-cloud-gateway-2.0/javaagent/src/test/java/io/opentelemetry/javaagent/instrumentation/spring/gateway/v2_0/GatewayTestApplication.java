/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.gateway.v2_0;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.route.builder.UriSpec;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class GatewayTestApplication {

  @Bean
  public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
    // A simple echo gateway.
    return builder
        .routes()
        .route(
            "path_route",
            r ->
                r.path("/gateway/**")
                    .filters(GatewayTestApplication::echoFunc)
                    .uri("h1c://mock.response"))
        // The routeID should be a random UUID.
        .route(
            r ->
                r.path("/uuid/**").filters(GatewayTestApplication::echoFunc).uri("h1c://mock.uuid"))
        // Seems like an uuid but not.
        .route(
            "ffffffff-ffff-ffff-ffff-ffff",
            r ->
                r.path("/fake/**").filters(GatewayTestApplication::echoFunc).uri("h1c://mock.fake"))
        .build();
  }

  private static UriSpec echoFunc(GatewayFilterSpec f) {
    return f.filter(
        (exchange, chain) -> exchange.getResponse().writeWith(exchange.getRequest().getBody()));
  }
}
