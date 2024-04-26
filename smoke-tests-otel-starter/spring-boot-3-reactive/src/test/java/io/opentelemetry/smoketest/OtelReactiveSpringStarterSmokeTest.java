/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.spring.smoketest.OtelReactiveSpringStarterSmokeTestApplication;
import io.opentelemetry.spring.smoketest.OtelReactiveSpringStarterSmokeTestController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(
    classes = {
      OtelReactiveSpringStarterSmokeTestApplication.class,
      OtelReactiveSpringStarterSmokeTest.TestConfiguration.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OtelReactiveSpringStarterSmokeTest {

  @RegisterExtension
  static final LibraryInstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @LocalServerPort int serverPort;

  @Autowired WebClient.Builder webClientBuilder;
  private WebClient webClient;

  @Configuration(proxyBeanMethods = false)
  static class TestConfiguration {
    @Bean
    OpenTelemetry openTelemetry() {
      return testing.getOpenTelemetry();
    }
  }

  @BeforeEach
  void setUp() {
    webClient = webClientBuilder.baseUrl("http://localhost:" + serverPort).build();
  }

  @AfterEach
  void tearDown(CapturedOutput output) {
    assertThat(output).doesNotContain("WARN").doesNotContain("ERROR");
  }

  @Test
  void webClientAndWebFluxAndR2dbc() {
    webClient
        .get()
        .uri(OtelReactiveSpringStarterSmokeTestController.WEBFLUX)
        .retrieve()
        .bodyToFlux(String.class)
        .blockLast();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .hasName("GET")
                        .hasAttributesSatisfying(
                            a -> assertThat(a.get(UrlAttributes.URL_FULL)).endsWith("/webflux")),
                span ->
                    span.hasKind(SpanKind.SERVER)
                        .hasName("GET /webflux")
                        .hasAttribute(HttpAttributes.HTTP_REQUEST_METHOD, "GET")
                        .hasAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 200L)
                        .hasAttribute(HttpAttributes.HTTP_ROUTE, "/webflux")));
  }
}
