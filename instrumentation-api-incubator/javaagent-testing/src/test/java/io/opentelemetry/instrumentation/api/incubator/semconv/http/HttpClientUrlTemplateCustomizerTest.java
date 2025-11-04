/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.http;

import static io.opentelemetry.semconv.incubating.UrlIncubatingAttributes.URL_TEMPLATE;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestServer;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.net.HttpURLConnection;
import java.net.URI;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class HttpClientUrlTemplateCustomizerTest {
  private static HttpClientTestServer server;

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @RegisterExtension
  static InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @BeforeAll
  static void setUp() {
    server = new HttpClientTestServer(testing.getOpenTelemetry());
    server.start();
  }

  @AfterAll
  static void tearDown() {
    server.stop();
  }

  @Test
  void test() throws Exception {
    URI uri = URI.create("http://localhost:" + server.httpPort() + "/hello/world");
    HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
    connection.getInputStream().close();
    int responseCode = connection.getResponseCode();
    connection.disconnect();

    assertThat(responseCode).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET /hello/*")
                        .hasNoParent()
                        .hasKind(SpanKind.CLIENT)
                        .hasAttribute(URL_TEMPLATE, "/hello/*")
                        .hasStatus(StatusData.unset()),
                span ->
                    span.hasName("test-http-server")
                        .hasParent(trace.getSpan(0))
                        .hasKind(SpanKind.SERVER)));
  }
}
