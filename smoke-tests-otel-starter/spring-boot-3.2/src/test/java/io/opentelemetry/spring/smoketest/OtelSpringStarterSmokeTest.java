/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.semconv.HttpAttributes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

@SpringBootTest(
    classes = {
      OtelSpringStarterSmokeTestApplication.class,
      AbstractOtelSpringStarterSmokeTest.TestConfiguration.class,
      SpringSmokeOtelConfiguration.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OtelSpringStarterSmokeTest extends AbstractSpringStarterSmokeTest {

  @Autowired RestClient.Builder restClientBuilder;
  @LocalServerPort private int port;

  @Test
  void restClient() {
    testing.clearAllExportedData();

    RestClient client = restClientBuilder.baseUrl("http://localhost:" + port).build();
    assertThat(
            client
                .get()
                .uri(OtelSpringStarterSmokeTestController.PING)
                .retrieve()
                .body(String.class))
        .isEqualTo("pong");

    if (System.getProperty("org.graalvm.nativeimage.imagecode") != null) {
      // ignore the trace for creating the db table
      testing.waitAndAssertTraces(trace -> {}, OtelSpringStarterSmokeTest::assertClient);
    } else {
      testing.waitAndAssertTraces(OtelSpringStarterSmokeTest::assertClient);
    }
  }

  private static void assertClient(TraceAssert traceAssert) {
    traceAssert.hasSpansSatisfyingExactly(
        span -> HttpSpanDataAssert.create(span).assertClientGetRequest("/ping"),
        span -> span.hasKind(SpanKind.SERVER).hasAttribute(HttpAttributes.HTTP_ROUTE, "/ping"),
        span -> withSpanAssert(span));
  }
}
