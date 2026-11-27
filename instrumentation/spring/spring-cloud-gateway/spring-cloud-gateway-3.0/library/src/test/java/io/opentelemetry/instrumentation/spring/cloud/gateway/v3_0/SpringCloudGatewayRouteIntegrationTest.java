/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.cloud.gateway.v3_0;

import static io.opentelemetry.semconv.HttpAttributes.HTTP_ROUTE;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.SpringWebfluxServerTelemetry;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = SpringCloudGatewayRouteIntegrationTest.TestApplication.class)
class SpringCloudGatewayRouteIntegrationTest {
  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Value("${local.server.port}")
  private int port;

  @Test
  void recordsMatchedRouteWhenGlobalFilterRejectsRequest() {
    // when
    AggregatedHttpResponse response =
        WebClient.of("h1c://localhost:" + port).get("/gateway/rejected").aggregate().join();

    // then
    assertThat(response.status().code()).isEqualTo(401);
    assertServerRoute("rejected-route");
  }

  @Test
  void preservesControllerTemplateOnLocalForward() {
    // when
    AggregatedHttpResponse response =
        WebClient.of("h1c://localhost:" + port).get("/gateway/forward").aggregate().join();

    // then
    assertThat(response.status().code()).isEqualTo(200);
    assertThat(response.contentUtf8()).isEqualTo("123");
    assertServerRoute("/local/{id}");
  }

  private static void assertServerRoute(String route) {
    testing.waitAndAssertTraces(trace ->
        trace.hasSpansSatisfyingExactly(span ->
            span.hasName("GET " + route).hasKind(SpanKind.SERVER).hasAttribute(HTTP_ROUTE, route)));
    testing.waitAndAssertMetrics(
        "io.opentelemetry.spring-webflux-5.3",
        metric ->
            metric
                .hasName("http.server.request.duration")
                .hasHistogramSatisfying(histogram ->
                    histogram.hasPointsSatisfying(point -> point.hasAttribute(HTTP_ROUTE, route))));
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class TestApplication {
    @Bean
    RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {
      return builder
          .routes()
          .route("rejected-route", route -> route.path("/gateway/rejected").uri("http://localhost"))
          .route("forward-route", route -> route.path("/gateway/forward").uri("forward:/local/123"))
          .build();
    }

    @Bean
    WebFilter telemetryFilter() {
      return SpringWebfluxServerTelemetry.builder(GlobalOpenTelemetry.get())
          .setRouteFallback(new SpringCloudGatewayRouteGetter())
          .build()
          .createWebFilterAndRegisterReactorHook();
    }

    @Bean
    GlobalFilter rejectRequestFilter() {
      return new RejectRequestFilter();
    }

    @Bean
    LocalController localController() {
      return new LocalController();
    }
  }

  private static final class RejectRequestFilter implements GlobalFilter, Ordered {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
      if ("/gateway/rejected".equals(exchange.getRequest().getPath().value())) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
      }
      return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
      return Ordered.HIGHEST_PRECEDENCE;
    }
  }

  @RestController
  static class LocalController {
    @GetMapping("/local/{id}")
    String local(@PathVariable("id") String id) {
      return id;
    }
  }
}
