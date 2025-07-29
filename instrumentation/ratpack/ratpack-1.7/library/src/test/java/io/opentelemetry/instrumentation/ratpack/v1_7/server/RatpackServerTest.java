/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.v1_7.server;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_ROUTE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_PATH;
import static io.opentelemetry.semconv.UrlAttributes.URL_QUERY;
import static io.opentelemetry.semconv.UrlAttributes.URL_SCHEME;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.ratpack.v1_7.RatpackServerTelemetry;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import ratpack.exec.Blocking;
import ratpack.registry.Registry;
import ratpack.test.embed.EmbeddedApp;

class RatpackServerTest {
  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static final RatpackServerTelemetry telemetry =
      RatpackServerTelemetry.builder(testing.getOpenTelemetry()).build();

  private static Registry registry;

  @BeforeAll
  static void setUp() throws Exception {
    registry = Registry.of(telemetry::configureRegistry);
  }

  @Test
  void testAddSpanOnHandlers() throws Exception {
    EmbeddedApp app =
        EmbeddedApp.of(
            spec -> {
              spec.registry(registry);
              spec.handlers(chain -> chain.get("foo", ctx -> ctx.render("hi-foo")));
            });
    cleanup.deferCleanup(app);

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
                            equalTo(SERVER_PORT, app.getServer().getBindPort()),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                            equalTo(URL_QUERY, ""),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200L))));
  }

  @Test
  void testPropagateTraceWithInstrumentedAsyncOperations() throws Exception {
    EmbeddedApp app =
        EmbeddedApp.of(
            spec -> {
              spec.registry(registry);
              spec.handlers(
                  chain ->
                      chain.get(
                          "foo",
                          ctx -> {
                            ctx.render("hi-foo");
                            Blocking.op(
                                    () ->
                                        testing.runWithSpan(
                                            "a-span",
                                            () -> {
                                              Span span = Span.current();
                                              span.addEvent("an-event");
                                            }))
                                .then();
                          }));
            });
    cleanup.deferCleanup(app);

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
                            equalTo(SERVER_PORT, app.getServer().getBindPort()),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                            equalTo(URL_QUERY, ""),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200L)),
                span ->
                    span.hasName("a-span")
                        .hasParent(trace.getSpan(0))
                        .hasAttributes(Attributes.empty())
                        .hasEventsSatisfyingExactly(
                            event -> event.hasName("an-event").hasAttributes(Attributes.empty()))));
  }

  @Test
  void testPropagateTraceWithInstrumentedAsyncConcurrentOperations() throws Exception {
    EmbeddedApp app =
        EmbeddedApp.of(
            spec -> {
              spec.registry(registry);
              spec.handlers(
                  chain -> {
                    chain.get(
                        "bar",
                        ctx -> {
                          ctx.render("hi-bar");
                          Blocking.op(
                                  () ->
                                      testing.runWithSpan(
                                          "another-span",
                                          () -> {
                                            Span span = Span.current();
                                            span.addEvent("an-event");
                                          }))
                              .then();
                        });
                    chain.get(
                        "foo",
                        ctx -> {
                          ctx.render("hi-foo");
                          Blocking.op(
                                  () ->
                                      testing.runWithSpan(
                                          "a-span",
                                          () -> {
                                            Span span = Span.current();
                                            span.addEvent("an-event");
                                          }))
                              .then();
                        });
                  });
            });
    cleanup.deferCleanup(app);

    assertThat(app.getHttpClient().get("foo").getBody().getText()).isEqualTo("hi-foo");
    assertThat(app.getHttpClient().get("bar").getBody().getText()).isEqualTo("hi-bar");

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
                            equalTo(SERVER_PORT, app.getServer().getBindPort()),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                            equalTo(URL_QUERY, ""),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200L)),
                span ->
                    span.hasName("a-span")
                        .hasParent(trace.getSpan(0))
                        .hasAttributes(Attributes.empty())
                        .hasEventsSatisfyingExactly(
                            event -> event.hasName("an-event").hasAttributes(Attributes.empty()))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET /bar")
                        .hasNoParent()
                        .hasKind(SpanKind.SERVER)
                        .hasAttributesSatisfyingExactly(
                            equalTo(HTTP_ROUTE, "/bar"),
                            equalTo(URL_PATH, "/bar"),
                            equalTo(URL_SCHEME, "http"),
                            equalTo(SERVER_PORT, app.getServer().getBindPort()),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                            equalTo(URL_QUERY, ""),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200L)),
                span ->
                    span.hasName("another-span")
                        .hasParent(trace.getSpan(0))
                        .hasAttributes(Attributes.empty())
                        .hasEventsSatisfyingExactly(
                            event -> event.hasName("an-event").hasAttributes(Attributes.empty()))));
  }
}
