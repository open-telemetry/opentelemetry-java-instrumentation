/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javahttpserver;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class JavaHttpServerHeaderSelectorTest {

  @RegisterExtension
  static final LibraryInstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private HttpServer server;

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void capturesHeadersMatchingSelectorPatterns() throws Exception {
    start(
        JavaHttpServerTelemetry.builder(testing.getOpenTelemetry())
            .setRequestHeaders(IncludeExclude.builder().setIncluded("x-test-*").build())
            .setResponseHeaders(IncludeExclude.builder().setExcluded("x-secret-*").build())
            .build()
            .createFilter());

    send();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasAttributesSatisfying(
                        attributes -> {
                          // matched by a wildcard include pattern, which requires enumerating the
                          // request header names
                          assertThat(headerValues(attributes, "http.request.header.x-test-request"))
                              .containsExactly("request-value");
                          assertThat(
                                  headerValues(attributes, "http.request.header.x-other-request"))
                              .isNull();
                          // matched by an exclude-only selector, which requires enumerating the
                          // response header names
                          assertThat(
                                  headerValues(attributes, "http.response.header.x-test-response"))
                              .containsExactly("response-value");
                          assertThat(
                                  headerValues(
                                      attributes, "http.response.header.x-secret-response"))
                              .isNull();
                        })));
  }

  @Test
  @SuppressWarnings("deprecation") // testing deprecated API
  void capturesHeadersConfiguredByName() throws Exception {
    start(
        JavaHttpServerTelemetry.builder(testing.getOpenTelemetry())
            .setCapturedRequestHeaders(singletonList("X-Test-Request"))
            .setCapturedResponseHeaders(singletonList("X-Test-Response"))
            .build()
            .createFilter());

    send();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasAttributesSatisfying(
                        attributes -> {
                          assertThat(headerValues(attributes, "http.request.header.x-test-request"))
                              .containsExactly("request-value");
                          assertThat(
                                  headerValues(attributes, "http.response.header.x-test-response"))
                              .containsExactly("response-value");
                        })));
  }

  @Test
  @SuppressWarnings("deprecation") // testing deprecated API
  void deprecatedSettersMatchHeaderNamesLiterally() throws Exception {
    start(
        JavaHttpServerTelemetry.builder(testing.getOpenTelemetry())
            .setCapturedRequestHeaders(singletonList("*"))
            .setCapturedResponseHeaders(singletonList("*"))
            .build()
            .createFilter());

    send();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasAttributesSatisfying(
                        attributes -> {
                          // implementing header name enumeration must not turn the deprecated
                          // exact-name setters into wildcard matching, since "*" is a legal header
                          // name character and capturing every header would expose credentials
                          assertThat(headerValues(attributes, "http.request.header.x-test-request"))
                              .isNull();
                          assertThat(headerValues(attributes, "http.request.header.authorization"))
                              .isNull();
                          assertThat(
                                  headerValues(attributes, "http.response.header.x-test-response"))
                              .isNull();
                        })));
  }

  private void start(Filter filter) throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    HttpContext context =
        server.createContext(
            "/",
            exchange -> {
              exchange.getResponseHeaders().add("X-Test-Response", "response-value");
              exchange.getResponseHeaders().add("X-Secret-Response", "secret-value");
              byte[] body = "hello".getBytes(UTF_8);
              exchange.sendResponseHeaders(200, body.length);
              exchange.getResponseBody().write(body);
              exchange.close();
            });
    context.getFilters().add(filter);
    server.start();
  }

  private void send() throws IOException {
    URL url = new URL("http://localhost:" + server.getAddress().getPort() + "/");
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setRequestProperty("X-Test-Request", "request-value");
    connection.setRequestProperty("X-Other-Request", "other-value");
    connection.setRequestProperty("Authorization", "Bearer secret-token");
    assertThat(connection.getResponseCode()).isEqualTo(200);
    try (InputStream inputStream = connection.getInputStream()) {
      while (inputStream.read() != -1) {
        // drain the response body
      }
    }
    connection.disconnect();
  }

  private static List<String> headerValues(Attributes attributes, String key) {
    return attributes.get(stringArrayKey(key));
  }
}
