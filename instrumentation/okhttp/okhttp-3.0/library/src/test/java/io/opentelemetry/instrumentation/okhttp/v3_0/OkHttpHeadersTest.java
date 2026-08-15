/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class OkHttpHeadersTest {

  @RegisterExtension
  static final LibraryInstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private static HttpServer server;

  @BeforeAll
  static void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
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
    server.start();
  }

  @AfterAll
  static void stopServer() {
    server.stop(0);
  }

  @Test
  void capturesHeadersMatchedThroughNameEnumeration() throws IOException {
    Call.Factory callFactory =
        OkHttpTelemetry.builder(testing.getOpenTelemetry())
            .setRequestHeaders(IncludeExclude.builder().setIncluded("x-test-*").build())
            .setResponseHeaders(IncludeExclude.builder().setExcluded("x-secret-*").build())
            .build()
            .createCallFactory(new OkHttpClient.Builder().build());

    send(callFactory);

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
  void capturesHeadersConfiguredWithDeprecatedSetters() throws IOException {
    Call.Factory callFactory =
        OkHttpTelemetry.builder(testing.getOpenTelemetry())
            .setCapturedRequestHeaders(singletonList("X-Test-Request"))
            .setCapturedResponseHeaders(singletonList("X-Test-Response"))
            .build()
            .createCallFactory(new OkHttpClient.Builder().build());

    send(callFactory);

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
  void deprecatedSettersMatchHeaderNamesLiterally() throws IOException {
    Call.Factory callFactory =
        OkHttpTelemetry.builder(testing.getOpenTelemetry())
            .setCapturedRequestHeaders(singletonList("*"))
            .setCapturedResponseHeaders(singletonList("*"))
            .build()
            .createCallFactory(new OkHttpClient.Builder().build());

    send(callFactory);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasAttributesSatisfying(
                        attributes -> {
                          // "*" is a legal header name character, so the deprecated setters look
                          // for a header literally named "*" instead of capturing every header
                          assertThat(headerValues(attributes, "http.request.header.x-test-request"))
                              .isNull();
                          assertThat(
                                  headerValues(attributes, "http.response.header.x-test-response"))
                              .isNull();
                        })));
  }

  private static void send(Call.Factory callFactory) throws IOException {
    Request request =
        new Request.Builder()
            .url("http://localhost:" + server.getAddress().getPort() + "/")
            .header("X-Test-Request", "request-value")
            .header("X-Other-Request", "other-value")
            .build();

    Response response = callFactory.newCall(request).execute();
    assertThat(response.code()).isEqualTo(200);
    response.body().close();
  }

  private static List<String> headerValues(Attributes attributes, String key) {
    return attributes.get(stringArrayKey(key));
  }
}
