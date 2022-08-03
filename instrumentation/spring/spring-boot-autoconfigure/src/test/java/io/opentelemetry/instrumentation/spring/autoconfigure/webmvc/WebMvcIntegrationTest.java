/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.webmvc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpRequest;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpMethod;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.ConfigurableApplicationContext;

class WebMvcIntegrationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  static int port;
  static ConfigurableApplicationContext applicationContext;

  @BeforeAll
  static void setUp() {
    port = PortUtils.findOpenPort();
    applicationContext = TestWebSpringBootApp.start(port);
  }

  @AfterAll
  static void tearDown() {
    applicationContext.close();
  }

  @Test
  void shouldSetHttpRoute() {
    AggregatedHttpRequest request =
        AggregatedHttpRequest.of(
            HttpMethod.GET, String.format("h1c://localhost:%d/test-app/test-route/42", port));
    WebClient client = WebClient.of();

    AggregatedHttpResponse response = client.execute(request).aggregate().join();

    assertEquals(200, response.status().code());
    assertEquals("42", response.contentUtf8());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("/test-app/test-route/{id}")
                        .hasKind(SpanKind.SERVER)
                        .hasAttribute(SemanticAttributes.HTTP_ROUTE, "/test-app/test-route/{id}")));
  }
}
