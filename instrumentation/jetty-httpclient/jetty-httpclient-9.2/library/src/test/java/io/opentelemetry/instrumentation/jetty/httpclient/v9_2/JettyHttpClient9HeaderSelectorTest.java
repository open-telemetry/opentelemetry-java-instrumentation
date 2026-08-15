/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.net.InetSocketAddress;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class JettyHttpClient9HeaderSelectorTest {

  @RegisterExtension
  static final LibraryInstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private HttpServer server;
  private HttpClient client;

  @BeforeEach
  void startServer() throws Exception {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/",
        exchange -> {
          exchange.getResponseHeaders().add("x-test-response", "response-value");
          exchange.getResponseHeaders().add("x-ignored-response", "ignored-value");
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    server.start();
  }

  @AfterEach
  void stopServer() throws Exception {
    try {
      if (client != null) {
        client.stop();
      }
    } finally {
      if (server != null) {
        server.stop(0);
      }
    }
  }

  @Test
  void capturesHeadersMatchingSelectors() throws Exception {
    client =
        JettyClientTelemetry.builder(testing.getOpenTelemetry())
            .setRequestHeaders(IncludeExclude.builder().setIncluded("x-test-*").build())
            .setResponseHeaders(IncludeExclude.builder().setExcluded("x-ignored-*").build())
            .build()
            .createHttpClient();

    sendRequest();

    assertCapturedHeaders();
  }

  @SuppressWarnings("deprecation") // testing deprecated API
  @Test
  void capturesHeadersFromDeprecatedSetters() throws Exception {
    client =
        JettyClientTelemetry.builder(testing.getOpenTelemetry())
            .setCapturedRequestHeaders(singletonList("x-test-request"))
            .setCapturedResponseHeaders(singletonList("x-test-response"))
            .build()
            .createHttpClient();

    sendRequest();

    assertCapturedHeaders();
  }

  @SuppressWarnings("deprecation") // testing deprecated API
  @Test
  void deprecatedSettersMatchHeaderNamesLiterally() throws Exception {
    client =
        JettyClientTelemetry.builder(testing.getOpenTelemetry())
            .setCapturedRequestHeaders(singletonList("*"))
            .setCapturedResponseHeaders(singletonList("*"))
            .build()
            .createHttpClient();

    sendRequest();

    assertNoCapturedHeaders();
  }

  private void sendRequest() throws Exception {
    client.start();

    ContentResponse response =
        client
            .newRequest("http://localhost:" + server.getAddress().getPort() + "/")
            .header("x-test-request", "request-value")
            .header("x-ignored-request", "ignored-value")
            .send();

    assertThat(response.getStatus()).isEqualTo(200);
  }

  private static void assertCapturedHeaders() {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasAttribute(
                            stringArrayKey("http.request.header.x-test-request"),
                            singletonList("request-value"))
                        .hasAttribute(
                            stringArrayKey("http.response.header.x-test-response"),
                            singletonList("response-value"))
                        .satisfies(
                            spanData ->
                                assertThat(spanData.getAttributes().asMap())
                                    .doesNotContainKey(
                                        stringArrayKey("http.request.header.x-ignored-request"))
                                    .doesNotContainKey(
                                        stringArrayKey(
                                            "http.response.header.x-ignored-response")))));
  }

  private static void assertNoCapturedHeaders() {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasAttributesSatisfying(
                        attributes ->
                            attributes.forEach(
                                (key, value) ->
                                    assertThat(key.getKey())
                                        .doesNotStartWith("http.request.header.")
                                        .doesNotStartWith("http.response.header.")))));
  }
}
