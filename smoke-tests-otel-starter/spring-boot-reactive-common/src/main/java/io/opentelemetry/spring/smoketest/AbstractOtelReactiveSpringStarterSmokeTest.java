/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static java.util.Objects.requireNonNull;

import io.opentelemetry.api.trace.SpanKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootTest(
    classes = {
      OtelReactiveSpringStarterSmokeTestApplication.class,
      SpringSmokeOtelConfiguration.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AbstractOtelReactiveSpringStarterSmokeTest extends AbstractSpringStarterSmokeTest {

  // can't use @LocalServerPort annotation since it moved packages between Spring Boot 2 and 3
  @Value("${local.server.port}")
  int serverPort;

  @Autowired WebClient.Builder webClientBuilder;
  private WebClient webClient;

  @BeforeEach
  void setUp() {
    webClient = webClientBuilder.baseUrl("http://localhost:" + serverPort).build();
  }

  @Test
  void webClientAndWebFluxAndR2dbc() {
    // Spring Data R2DBC 1.x uses PostgreSQL's lowercase identifier rules for H2;
    // 3.x uses H2's uppercase rules.
    boolean springBoot2 = requireNonNull(SpringBootVersion.getVersion()).startsWith("2.");

    webClient
        .get()
        .uri(OtelReactiveSpringStarterSmokeTestController.WEBFLUX)
        .retrieve()
        .bodyToFlux(String.class)
        .blockLast();

    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("CREATE TABLE player")),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> HttpSpanDataAssert.create(span).assertClientGetRequest("/webflux"),
                span -> HttpSpanDataAssert.create(span).assertServerGetRequest("/webflux"),
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .hasName(springBoot2 ? "SELECT player" : "SELECT PLAYER")
                        .hasAttribute(DB_NAMESPACE, "testdb")
                        // 2 is not replaced by ?,
                        // otel.instrumentation.common.db.query-sanitization.enabled=false
                        .hasAttribute(
                            DB_QUERY_TEXT,
                            springBoot2
                                ? "SELECT player.* FROM player WHERE player.id = $1 LIMIT 2"
                                : "SELECT PLAYER.* FROM PLAYER WHERE PLAYER.ID = $1 LIMIT 2")
                        .hasAttribute(DB_SYSTEM_NAME, "h2database")));
  }
}
