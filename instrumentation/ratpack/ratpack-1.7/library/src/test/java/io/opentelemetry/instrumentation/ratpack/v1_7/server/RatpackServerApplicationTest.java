/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.v1_7.server;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_ROUTE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.UrlAttributes.URL_PATH;
import static io.opentelemetry.semconv.UrlAttributes.URL_QUERY;
import static io.opentelemetry.semconv.UrlAttributes.URL_SCHEME;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class RatpackServerApplicationTest {
  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private static RatpackFunctionalTest app;

  @BeforeAll
  static void setup() throws Exception {
    app = new RatpackFunctionalTest(RatpackApp.class);
  }

  @AfterAll
  static void cleanup() {
    app.close();
  }

  @Test
  void testAddSpanOnHandlers() {
    assertThat(app.getHttpClient().get("foo").getBody().getText()).isEqualTo("hi-foo");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET /foo")
                        .hasNoParent()
                        .hasKind(SpanKind.SERVER)
                        .hasAttributesSatisfyingExactly(
                            equalTo(HTTP_ROUTE, "/foo"),
                            equalTo(URL_PATH, "/foo"),
                            equalTo(URL_SCHEME, "http"),
                            satisfies(SERVER_PORT, v -> v.isInstanceOf(Long.class)),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                            equalTo(URL_QUERY, ""),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200L))));
  }

  @Test
  void testPropagateTraceToHttpCalls() {
    assertThat(app.getHttpClient().get("bar").getBody().getText()).isEqualTo("hi-bar");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET /bar")
                        .hasNoParent()
                        .hasKind(SpanKind.SERVER)
                        .hasAttributesSatisfyingExactly(
                            equalTo(HTTP_ROUTE, "/bar"),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200L),
                            equalTo(URL_PATH, "/bar"),
                            equalTo(URL_SCHEME, "http"),
                            satisfies(SERVER_PORT, v -> v.isInstanceOf(Long.class)),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                            equalTo(URL_QUERY, "")),
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(HTTP_ROUTE, "/other"),
                            equalTo(URL_FULL, "http://localhost:" + app.getAppPort() + "/other"),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200L),
                            satisfies(SERVER_PORT, v -> v.isInstanceOf(Long.class)),
                            equalTo(SERVER_ADDRESS, "localhost"))));
  }

  @Test
  void testIgnoreHandlersBeforeOpenTelemetryServerHandler() {
    testing.runWithSpan(
        "parent",
        () ->
            assertThat(app.getHttpClient().get("ignore").getBody().getText()).isEqualTo("ignored"));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("parent")
                        .hasNoParent()
                        .hasKind(SpanKind.INTERNAL)
                        .hasAttributes(Attributes.empty())));
  }
}
