/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc.v5_3;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_ROUTE;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import javax.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = ActuatorHttpRouteTest.ActuatorTestApp.class)
class ActuatorHttpRouteTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Autowired private TestRestTemplate testRestTemplate;

  @Test
  void actuatorEndpointHasHttpRoute() {
    ResponseEntity<String> response =
        testRestTemplate.getForEntity("/actuator/health", String.class);
    assertThat(response.getStatusCode().value()).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET /actuator/health")
                        .hasKind(SpanKind.SERVER)
                        .hasAttribute(equalTo(HTTP_ROUTE, "/actuator/health"))));
  }

  // Avoid @SpringBootApplication so TestWebSpringBootApp's component scan doesn't pick up
  // this configuration and register a second telemetry filter.
  @EnableAutoConfiguration
  static class ActuatorTestApp {

    @Bean
    Filter telemetryFilter() {
      return SpringWebMvcTelemetry.create(GlobalOpenTelemetry.get()).createServletFilter();
    }
  }
}
