/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Mono;

@SpringBootApplication
public class OtelReactiveSpringStarterSmokeTestApplication {

  public OtelReactiveSpringStarterSmokeTestApplication() {}

  public static void main(String[] args) {
    SpringApplication.run(OtelReactiveSpringStarterSmokeTestApplication.class);
  }

  @Bean
  RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {
    return builder
        .routes()
        .route(
            "test-gateway-route",
            route ->
                route
                    .path("/gateway/**")
                    .filters(filters ->
                        filters.filter((exchange, chain) -> {
                          byte[] body = "gateway".getBytes(UTF_8);
                          return exchange
                              .getResponse()
                              .writeWith(
                                  Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
                        }))
                    .uri("http://localhost"))
        .build();
  }
}
