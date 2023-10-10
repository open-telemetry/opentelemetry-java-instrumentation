/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.gateway.v2_0;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
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
                    .filters(
                        f ->
                            f.filter(
                                (exchange, chain) ->
                                    exchange
                                        .getResponse()
                                        .writeWith(exchange.getRequest().getBody())))
                    .uri("h1c://mock.response"))
        .build();
  }
}
