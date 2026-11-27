/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc.v6_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_ROUTE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.web.servlet.function.RequestPredicates.GET;
import static org.springframework.web.servlet.function.RouterFunctions.route;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import jakarta.servlet.Filter;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = FunctionalHttpRouteTest.TestApplication.class,
    properties = "server.servlet.context-path=/app")
class FunctionalHttpRouteTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Value("${local.server.port}")
  private int port;

  @Autowired private AtomicInteger matches;

  @Test
  void functionalEndpointUsesMatchedRoute() {
    AggregatedHttpResponse response =
        WebClient.of("http://localhost:" + port).get("/app/items/42").aggregate().join();
    assertThat(response.status().code()).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET /app/items/{id}")
                        .hasKind(SpanKind.SERVER)
                        .hasAttribute(equalTo(HTTP_ROUTE, "/app/items/{id}"))));
    assertThat(matches.get()).isEqualTo(1);
  }

  @EnableAutoConfiguration
  static class TestApplication {

    @Bean
    Filter telemetryFilter() {
      return SpringWebMvcTelemetry.create(GlobalOpenTelemetry.get()).createServletFilter();
    }

    @Bean
    AtomicInteger matches() {
      return new AtomicInteger();
    }

    @Bean
    RouterFunction<ServerResponse> itemsRoute(AtomicInteger matches) {
      return route(
          GET("/items/{id}")
              .and(
                  request -> {
                    matches.incrementAndGet();
                    return true;
                  }),
          request -> ServerResponse.ok().body(request.pathVariable("id")));
    }
  }
}
