/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.v1_7.client;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
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
import static org.junit.jupiter.api.Named.named;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.ratpack.v1_7.RatpackClientTelemetry;
import io.opentelemetry.instrumentation.ratpack.v1_7.RatpackServerTelemetry;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.net.URI;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ratpack.exec.Promise;
import ratpack.func.Action;
import ratpack.guice.Guice;
import ratpack.http.client.HttpClient;
import ratpack.http.client.HttpClientReadTimeoutException;
import ratpack.http.client.ReceivedResponse;
import ratpack.test.embed.EmbeddedApp;

class InstrumentedHttpClientTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static final RatpackClientTelemetry clientTelemetry =
      RatpackClientTelemetry.create(testing.getOpenTelemetry());

  private static final RatpackServerTelemetry serverTelemetry =
      RatpackServerTelemetry.create(testing.getOpenTelemetry());

  @Test
  void testPropagateTraceWithHttpCalls() throws Exception {
    EmbeddedApp otherApp =
        EmbeddedApp.of(
            spec -> {
              spec.registry(Guice.registry(serverTelemetry::configureRegistry));
              spec.handlers(chain -> chain.get("bar", ctx -> ctx.render("foo")));
            });
    cleanup.deferCleanup(otherApp);

    EmbeddedApp app =
        EmbeddedApp.of(
            spec -> {
              spec.registry(
                  Guice.registry(
                      bindings -> {
                        serverTelemetry.configureRegistry(bindings);
                        bindings.bindInstance(
                            HttpClient.class,
                            clientTelemetry.instrument(HttpClient.of(Action.noop())));
                      }));

              spec.handlers(
                  chain ->
                      chain.get(
                          "foo",
                          ctx -> {
                            HttpClient instrumentedHttpClient = ctx.get(HttpClient.class);
                            instrumentedHttpClient
                                .get(new URI(otherApp.getAddress() + "bar"))
                                .then(response -> ctx.render("bar"));
                          }));
            });
    cleanup.deferCleanup(app);

    assertThat(app.getHttpClient().get("foo").getBody().getText()).isEqualTo("bar");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET /foo")
                        .hasNoParent()
                        .hasKind(SpanKind.SERVER)
                        .hasAttributesSatisfyingExactly(
                            equalTo(HTTP_ROUTE, "/foo"),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200L),
                            equalTo(SERVER_PORT, app.getServer().getBindPort()),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                            equalTo(URL_PATH, "/foo"),
                            equalTo(URL_SCHEME, "http"),
                            equalTo(URL_QUERY, "")),
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(HTTP_ROUTE, "/bar"),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200L),
                            satisfies(SERVER_PORT, v -> v.isInstanceOf(Long.class)),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(URL_FULL, otherApp.getAddress() + "bar")),
                span ->
                    span.hasName("GET /bar")
                        .hasParent(trace.getSpan(1))
                        .hasKind(SpanKind.SERVER)
                        .hasAttributesSatisfyingExactly(
                            equalTo(HTTP_ROUTE, "/bar"),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200L),
                            equalTo(SERVER_PORT, otherApp.getServer().getBindPort()),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                            equalTo(URL_PATH, "/bar"),
                            equalTo(URL_SCHEME, "http"),
                            equalTo(URL_QUERY, ""))));
  }

  @Test
  void testAddSpansForMultipleConcurrentClientCalls() throws Exception {
    EmbeddedApp otherApp =
        EmbeddedApp.of(
            spec ->
                spec.handlers(
                    chain -> {
                      chain.get("foo", ctx -> ctx.render("bar"));
                      chain.get("bar", ctx -> ctx.render("foo"));
                    }));
    cleanup.deferCleanup(otherApp);

    EmbeddedApp app =
        EmbeddedApp.of(
            spec -> {
              spec.registry(
                  Guice.registry(
                      bindings -> {
                        serverTelemetry.configureRegistry(bindings);
                        bindings.bindInstance(
                            HttpClient.class,
                            clientTelemetry.instrument(HttpClient.of(Action.noop())));
                      }));

              spec.handlers(
                  chain ->
                      chain.get(
                          "path-name",
                          ctx -> {
                            ctx.render("hello");
                            HttpClient instrumentedHttpClient = ctx.get(HttpClient.class);

                            instrumentedHttpClient
                                .get(new URI(otherApp.getAddress() + "foo"))
                                .then(ReceivedResponse::getBody);

                            instrumentedHttpClient
                                .get(new URI(otherApp.getAddress() + "bar"))
                                .then(ReceivedResponse::getBody);
                          }));
            });
    cleanup.deferCleanup(app);

    assertThat(app.getHttpClient().get("path-name").getBody().getText()).isEqualTo("hello");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET /path-name")
                        .hasNoParent()
                        .hasKind(SpanKind.SERVER)
                        .hasAttributesSatisfyingExactly(
                            equalTo(HTTP_ROUTE, "/path-name"),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200L),
                            equalTo(URL_QUERY, ""),
                            equalTo(URL_PATH, "/path-name"),
                            equalTo(URL_SCHEME, "http"),
                            equalTo(SERVER_PORT, app.getServer().getBindPort()),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(NETWORK_PROTOCOL_VERSION, "1.1")),
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(HTTP_ROUTE, "/foo"),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200L),
                            satisfies(SERVER_PORT, v -> v.isInstanceOf(Long.class)),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(URL_FULL, otherApp.getAddress() + "foo")),
                span ->
                    span.hasName("GET")
                        .hasParent(trace.getSpan(0))
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(HTTP_ROUTE, "/bar"),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200L),
                            satisfies(SERVER_PORT, v -> v.isInstanceOf(Long.class)),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(URL_FULL, otherApp.getAddress() + "bar"))));
  }

  @Test
  void testHandlingExceptionErrorsInHttpClient() throws Exception {
    EmbeddedApp otherApp =
        EmbeddedApp.of(
            spec ->
                spec.handlers(
                    chain ->
                        chain.get(
                            "foo",
                            ctx ->
                                Promise.value("bar")
                                    .defer(Duration.ofSeconds(1L))
                                    .then(value -> ctx.render("bar")))));
    cleanup.deferCleanup(otherApp);

    EmbeddedApp app =
        EmbeddedApp.of(
            spec -> {
              spec.registry(
                  Guice.registry(
                      bindings -> {
                        serverTelemetry.configureRegistry(bindings);
                        bindings.bindInstance(
                            HttpClient.class,
                            clientTelemetry.instrument(
                                HttpClient.of(s -> s.readTimeout(Duration.ofMillis(10)))));
                      }));

              spec.handlers(
                  chain ->
                      chain.get(
                          "error-path-name",
                          ctx -> {
                            HttpClient instrumentedHttpClient = ctx.get(HttpClient.class);
                            instrumentedHttpClient
                                .get(new URI(otherApp.getAddress() + "foo"))
                                .onError(throwable -> ctx.render("error"))
                                .then(response -> ctx.render("hello"));
                          }));
            });
    cleanup.deferCleanup(app);

    assertThat(app.getHttpClient().get("error-path-name").getBody().getText()).isEqualTo("error");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET /error-path-name")
                        .hasNoParent()
                        .hasKind(SpanKind.SERVER)
                        .hasAttributesSatisfyingExactly(
                            equalTo(HTTP_ROUTE, "/error-path-name"),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200L),
                            equalTo(SERVER_PORT, app.getServer().getBindPort()),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                            equalTo(URL_PATH, "/error-path-name"),
                            equalTo(URL_SCHEME, "http"),
                            equalTo(URL_QUERY, "")),
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasStatus(StatusData.error())
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(HTTP_ROUTE, "/foo"),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(ERROR_TYPE, HttpClientReadTimeoutException.class.getName()),
                            satisfies(SERVER_PORT, v -> v.isInstanceOf(Long.class)),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(URL_FULL, otherApp.getAddress() + "foo"))));
  }

  private static Stream<Arguments> provideArguments() {
    return Stream.of(
        Arguments.of(named("Compute Thread", BarService.class)),
        Arguments.of(named("Fork Executions", BarForkService.class)));
  }

  @ParameterizedTest
  @MethodSource("provideArguments")
  void propagateHttpTraceInRatpackServicesWithForkExecutions(
      Class<? extends BarService> serviceClass) throws Exception {
    EmbeddedApp otherApp =
        EmbeddedApp.of(spec -> spec.handlers(chain -> chain.get("foo", ctx -> ctx.render("bar"))));
    cleanup.deferCleanup(otherApp);

    EmbeddedApp app =
        EmbeddedApp.of(
            spec -> {
              spec.registry(
                  Guice.registry(
                      bindings -> {
                        serverTelemetry.configureRegistry(bindings);
                        bindings.bindInstance(
                            HttpClient.class,
                            clientTelemetry.instrument(HttpClient.of(Action.noop())));
                        bindings.bindInstance(
                            serviceClass
                                .getConstructor(String.class, InstrumentationExtension.class)
                                .newInstance(otherApp.getAddress() + "foo", testing));
                      }));

              spec.handlers(chain -> chain.get("foo", ctx -> ctx.render("bar")));
            });
    cleanup.deferCleanup(app);

    app.getAddress();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("a-span").hasNoParent().hasAttributes(Attributes.empty()),
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(HTTP_ROUTE, "/foo"),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200L),
                            satisfies(SERVER_PORT, v -> v.isInstanceOf(Long.class)),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(URL_FULL, otherApp.getAddress() + "foo")),
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(HTTP_ROUTE, "/foo"),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200L),
                            satisfies(SERVER_PORT, v -> v.isInstanceOf(Long.class)),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(URL_FULL, otherApp.getAddress() + "foo"))));
  }
}
