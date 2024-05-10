/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootTest(
    classes = {
      OtelReactiveSpringStarterSmokeTestApplication.class,
      SpringSmokeOtelConfiguration.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AbstractOtelReactiveSpringStarterSmokeTest extends AbstractSpringStarterSmokeTest {

  @LocalServerPort int serverPort;

  @Autowired WebClient.Builder webClientBuilder;
  private WebClient webClient;

  @BeforeEach
  void setUp() {
    webClient = webClientBuilder.baseUrl("http://localhost:" + serverPort).build();
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
            trace.hasSpansSatisfyingExactly(span -> span.hasName("CREATE TABLE testdb.player")),
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
                        .hasAttribute(HttpAttributes.HTTP_ROUTE, "/webflux"),
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .satisfies(
                            s ->
                                assertThat(s.getName())
                                    .isEqualToIgnoringCase("SELECT testdb.PLAYER"))
                        .hasAttribute(DbIncubatingAttributes.DB_NAME, "testdb")
                        .hasAttributesSatisfying(
                            a ->
                                assertThat(a.get(DbIncubatingAttributes.DB_SQL_TABLE))
                                    .isEqualToIgnoringCase("PLAYER"))
                        .hasAttribute(DbIncubatingAttributes.DB_OPERATION, "SELECT")
                        .hasAttributesSatisfying(
                            a ->
                                assertThat(a.get(DbIncubatingAttributes.DB_STATEMENT))
                                    .isEqualToIgnoringCase(
                                        "SELECT PLAYER.* FROM PLAYER WHERE PLAYER.ID = $? LIMIT ?"))
                        .hasAttribute(DbIncubatingAttributes.DB_SYSTEM, "h2")));
  }
}
