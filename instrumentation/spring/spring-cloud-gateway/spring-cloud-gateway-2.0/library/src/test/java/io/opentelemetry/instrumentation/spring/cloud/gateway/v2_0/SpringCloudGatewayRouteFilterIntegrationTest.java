/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.cloud.gateway.v2_0;

import static io.opentelemetry.semconv.HttpAttributes.HTTP_ROUTE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.SpringWebfluxServerTelemetry;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = SpringCloudGatewayRouteFilterIntegrationTest.TestApplication.class)
class SpringCloudGatewayRouteFilterIntegrationTest {
  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Value("${local.server.port}")
  private int port;

  @Test
  void enrichesServerSpanAndMetricWhenRouteShortCircuits() {
    // given
    String routeId = "test-gateway-route";

    // when
    AggregatedHttpResponse response =
        WebClient.of("h1c://localhost:" + port).get("/gateway/test").aggregate().join();

    // then
    assertThat(response.status().code()).isEqualTo(200);
    assertThat(response.contentUtf8()).isEqualTo("gateway");

    AtomicReference<String> instrumentationName = new AtomicReference<>();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> {
                  span.hasName("GET " + routeId)
                      .hasKind(SpanKind.SERVER)
                      .hasAttribute(HTTP_ROUTE, routeId);
                  instrumentationName.set(span.actual().getInstrumentationScopeInfo().getName());
                }));
    testing.waitAndAssertMetrics(
        instrumentationName.get(),
        metric ->
            metric
                .hasName("http.server.request.duration")
                .hasHistogramSatisfying(
                    histogram ->
                        histogram.hasPointsSatisfying(
                            point -> point.hasAttribute(HTTP_ROUTE, routeId))));
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class TestApplication {
    @Bean
    RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {
      return builder
          .routes()
          .route(
              "test-gateway-route",
              route ->
                  route
                      .path("/gateway/**")
                      .filters(
                          filters ->
                              filters.filter(
                                  (exchange, chain) ->
                                      exchange
                                          .getResponse()
                                          .writeWith(
                                              Mono.just(
                                                  exchange
                                                      .getResponse()
                                                      .bufferFactory()
                                                      .wrap("gateway".getBytes(UTF_8))))))
                      .uri("http://localhost"))
          .build();
    }

    @Bean
    WebFilter telemetryFilter() {
      return SpringWebfluxServerTelemetry.create(GlobalOpenTelemetry.get())
          .createWebFilterAndRegisterReactorHook();
    }

    @Bean
    SpringCloudGatewayRouteFilter springCloudGatewayRouteFilter() {
      return new SpringCloudGatewayRouteFilter();
    }
  }
}
