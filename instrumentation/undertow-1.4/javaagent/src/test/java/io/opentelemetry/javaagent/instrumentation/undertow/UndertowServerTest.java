/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.undertow;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ClientAttributes.CLIENT_ADDRESS;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_PATH;
import static io.opentelemetry.semconv.UrlAttributes.URL_SCHEME;
import static io.opentelemetry.semconv.UserAgentAttributes.USER_AGENT_ORIGINAL;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableSet;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class UndertowServerTest extends AbstractHttpServerTest<Undertow> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  @Override
  public Undertow setupServer() {
    Undertow.Builder builder =
        Undertow.builder()
            .addHttpListener(port, "localhost")
            .setHandler(
                Handlers.path()
                    .addExactPath(
                        SUCCESS.rawPath(),
                        exchange ->
                            controller(
                                SUCCESS,
                                () -> exchange.getResponseSender().send(SUCCESS.getBody())))
                    .addExactPath(
                        QUERY_PARAM.rawPath(),
                        exchange ->
                            controller(
                                QUERY_PARAM,
                                () -> exchange.getResponseSender().send(exchange.getQueryString())))
                    .addExactPath(
                        REDIRECT.rawPath(),
                        exchange ->
                            controller(
                                REDIRECT,
                                () -> {
                                  exchange.setStatusCode(StatusCodes.FOUND);
                                  exchange
                                      .getResponseHeaders()
                                      .put(Headers.LOCATION, REDIRECT.getBody());
                                  exchange.endExchange();
                                }))
                    .addExactPath(
                        CAPTURE_HEADERS.rawPath(),
                        exchange ->
                            controller(
                                CAPTURE_HEADERS,
                                () -> {
                                  exchange.setStatusCode(StatusCodes.OK);
                                  exchange
                                      .getResponseHeaders()
                                      .put(
                                          new HttpString("X-Test-Response"),
                                          exchange.getRequestHeaders().getFirst("X-Test-Request"));
                                  exchange.getResponseSender().send(CAPTURE_HEADERS.getBody());
                                }))
                    .addExactPath(
                        ERROR.rawPath(),
                        exchange ->
                            controller(
                                ERROR,
                                () -> {
                                  exchange.setStatusCode(ERROR.getStatus());
                                  exchange.getResponseSender().send(ERROR.getBody());
                                }))
                    .addExactPath(
                        EXCEPTION.rawPath(),
                        exchange ->
                            testing.runWithSpan(
                                "controller",
                                () -> {
                                  throw new IllegalStateException(EXCEPTION.getBody());
                                }))
                    .addExactPath(
                        INDEXED_CHILD.rawPath(),
                        exchange ->
                            controller(
                                INDEXED_CHILD,
                                () -> {
                                  INDEXED_CHILD.collectSpanAttributes(
                                      name -> exchange.getQueryParameters().get(name).peekFirst());
                                  exchange.getResponseSender().send(INDEXED_CHILD.getBody());
                                }))
                    .addExactPath(
                        "sendResponse",
                        exchange -> {
                          Span.current().addEvent("before-event");
                          testing.runWithSpan(
                              "sendResponse",
                              () -> {
                                exchange.setStatusCode(StatusCodes.OK);
                                exchange.getResponseSender().send("sendResponse");
                              });
                          // event is added only when server span has not been ended
                          // we need to make sure that sending response does not end server span
                          Span.current().addEvent("after-event");
                        })
                    .addExactPath(
                        "sendResponseWithException",
                        exchange -> {
                          Span.current().addEvent("before-event");
                          testing.runWithSpan(
                              "sendResponseWithException",
                              () -> {
                                exchange.setStatusCode(StatusCodes.OK);
                                exchange.getResponseSender().send("sendResponseWithException");
                              });
                          // event is added only when server span has not been ended
                          // we need to make sure that sending response does not end server span
                          Span.current().addEvent("after-event");
                          throw new Exception("exception after sending response");
                        }));
    configureUndertow(builder);
    Undertow server = builder.build();
    server.start();
    return server;
  }

  @Override
  public void stopServer(Undertow undertow) {
    undertow.stop();
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setHttpAttributes(endpoint -> ImmutableSet.of(NETWORK_PEER_PORT));
    options.setHasResponseCustomizer(serverEndpoint -> true);
    options.setUseHttp2(useHttp2());
  }

  protected void configureUndertow(Undertow.Builder builder) {}

  protected boolean useHttp2() {
    return false;
  }

  @DisplayName("test send response")
  @Test
  void testSendResponse() {
    URI uri = address.resolve("sendResponse");
    AggregatedHttpResponse response = client.get(uri.toString()).aggregate().join();

    assertThat(response.status().code()).isEqualTo(200);
    assertThat(response.contentUtf8().trim()).isEqualTo("sendResponse");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET")
                        .hasNoParent()
                        .hasKind(SpanKind.SERVER)
                        .hasEventsSatisfyingExactly(
                            event -> event.hasName("before-event"),
                            event -> event.hasName("after-event"))
                        .hasAttributesSatisfyingExactly(
                            equalTo(CLIENT_ADDRESS, TEST_CLIENT_IP),
                            equalTo(URL_SCHEME, uri.getScheme()),
                            equalTo(URL_PATH, uri.getPath()),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                            equalTo(USER_AGENT_ORIGINAL, TEST_USER_AGENT),
                            equalTo(NETWORK_PROTOCOL_VERSION, useHttp2() ? "2" : "1.1"),
                            equalTo(SERVER_ADDRESS, uri.getHost()),
                            equalTo(SERVER_PORT, uri.getPort()),
                            equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"),
                            satisfies(NETWORK_PEER_PORT, k -> k.isInstanceOf(Long.class))),
                span ->
                    span.hasName("sendResponse")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  @DisplayName("test send response with exception")
  void testSendResponseWithException() {
    URI uri = address.resolve("sendResponseWithException");
    AggregatedHttpResponse response = client.get(uri.toString()).aggregate().join();

    assertThat(response.status().code()).isEqualTo(200);
    assertThat(response.contentUtf8().trim()).isEqualTo("sendResponseWithException");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET")
                        .hasNoParent()
                        .hasKind(SpanKind.SERVER)
                        .hasEventsSatisfyingExactly(
                            event -> event.hasName("before-event"),
                            event -> event.hasName("after-event"),
                            event ->
                                event
                                    .hasName("exception")
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(EXCEPTION_TYPE, Exception.class.getName()),
                                        equalTo(
                                            EXCEPTION_MESSAGE, "exception after sending response"),
                                        satisfies(
                                            EXCEPTION_STACKTRACE,
                                            val -> val.isInstanceOf(String.class))))
                        .hasAttributesSatisfyingExactly(
                            equalTo(CLIENT_ADDRESS, TEST_CLIENT_IP),
                            equalTo(URL_SCHEME, uri.getScheme()),
                            equalTo(URL_PATH, uri.getPath()),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                            equalTo(USER_AGENT_ORIGINAL, TEST_USER_AGENT),
                            equalTo(NETWORK_PROTOCOL_VERSION, useHttp2() ? "2" : "1.1"),
                            equalTo(SERVER_ADDRESS, uri.getHost()),
                            equalTo(SERVER_PORT, uri.getPort()),
                            equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"),
                            satisfies(NETWORK_PEER_PORT, k -> k.isInstanceOf(Long.class))),
                span ->
                    span.hasName("sendResponseWithException")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }
}
