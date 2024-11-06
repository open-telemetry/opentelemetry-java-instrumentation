/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.api.trace.SpanKind.SERVER;
import static io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0.AbstractReactorNettyHttpClientTest.USER_AGENT;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TRANSPORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.netty.handler.ssl.SslContextBuilder;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestServer;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.List;
import javax.annotation.Nullable;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import org.assertj.core.api.AbstractLongAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;
import reactor.netty.tcp.SslProvider;

class ReactorNettyClientSslTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  static HttpClientTestServer server;

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
  void shouldFailSslHandshake() throws SSLException {
    HttpClient httpClient = createHttpClient("SSLv3");
    String uri = "https://localhost:" + server.httpsPort() + "/success";

    Mono<HttpClientResponse> responseMono =
        httpClient
            .get()
            .uri(uri)
            .responseSingle(
                (resp, content) -> {
                  // Make sure to consume content since that's when we close the span.
                  return content.map(unused -> resp);
                });

    Throwable thrown =
        catchThrowable(() -> testing.runWithSpan("parent", () -> responseMono.block()));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactlyInAnyOrder(
                span ->
                    span.hasName("parent")
                        .hasKind(INTERNAL)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(thrown),
                span ->
                    span.hasName("GET")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error())
                        // netty swallows the exception, it doesn't make any sense to hard-code the
                        // message
                        .hasEventsSatisfying(ReactorNettyClientSslTest::isSslHandshakeException)
                        .hasAttributesSatisfyingExactly(
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(URL_FULL, uri),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(SERVER_PORT, server.httpsPort()),
                            equalTo(ERROR_TYPE, SSLHandshakeException.class.getCanonicalName())),
                span ->
                    span.hasName("RESOLVE")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(SERVER_PORT, server.httpsPort())),
                span ->
                    span.hasName("CONNECT")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TRANSPORT, "tcp"),
                            equalTo(NETWORK_TYPE, "ipv4"),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(SERVER_PORT, server.httpsPort()),
                            equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"),
                            satisfies(NETWORK_PEER_PORT, AbstractLongAssert::isNotNegative)),
                span ->
                    span.hasName("SSL handshake")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error())
                        // netty swallows the exception, it doesn't make any sense to hard-code the
                        // message
                        .hasEventsSatisfying(ReactorNettyClientSslTest::isSslHandshakeException)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TRANSPORT, "tcp"),
                            equalTo(NETWORK_TYPE, "ipv4"),
                            equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"),
                            equalTo(NETWORK_PEER_PORT, server.httpsPort()))));
  }

  @Test
  void shouldSuccessfullyEstablishSslHandshake() throws SSLException {
    HttpClient httpClient = createHttpClient();
    String uri = "https://localhost:" + server.httpsPort() + "/success";

    Mono<HttpClientResponse> responseMono =
        httpClient
            .headers(h -> h.set("User-Agent", USER_AGENT))
            .get()
            .uri(uri)
            .responseSingle(
                (resp, content) -> {
                  // Make sure to consume content since that's when we close the span.
                  return content.map(unused -> resp);
                });

    testing.runWithSpan("parent", () -> responseMono.block());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactlyInAnyOrder(
                span -> span.hasName("parent").hasKind(INTERNAL).hasNoParent(),
                span ->
                    span.hasName("RESOLVE")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(SERVER_PORT, server.httpsPort())),
                span ->
                    span.hasName("CONNECT")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TRANSPORT, "tcp"),
                            equalTo(NETWORK_TYPE, "ipv4"),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(SERVER_PORT, server.httpsPort()),
                            equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"),
                            satisfies(NETWORK_PEER_PORT, AbstractLongAssert::isNotNegative)),
                span ->
                    span.hasName("SSL handshake")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TRANSPORT, "tcp"),
                            equalTo(NETWORK_TYPE, "ipv4"),
                            equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"),
                            equalTo(NETWORK_PEER_PORT, server.httpsPort())),
                span ->
                    span.hasName("GET")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(URL_FULL, uri),
                            equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(SERVER_PORT, server.httpsPort()),
                            equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"),
                            satisfies(NETWORK_PEER_PORT, AbstractLongAssert::isNotNegative)),
                span ->
                    span.hasName("test-http-server").hasKind(SERVER).hasParent(trace.getSpan(4))));
  }

  private static HttpClient createHttpClient() throws SSLException {
    return ReactorNettyClientSslTest.createHttpClient(null);
  }

  private static HttpClient createHttpClient(@Nullable String enabledProtocol) throws SSLException {
    SslContextBuilder sslContext = SslContextBuilder.forClient();
    if (enabledProtocol != null) {
      sslContext = sslContext.protocols(enabledProtocol);
    }

    SslProvider sslProvider = SslProvider.builder().sslContext(sslContext.build()).build();
    return HttpClient.create().secure(sslProvider);
  }

  private static void isSslHandshakeException(List<? extends EventData> events) {
    assertThat(events)
        .filteredOn(event -> event.getName().equals("exception"))
        .satisfiesExactly(
            event ->
                assertThat(event)
                    .hasAttributesSatisfyingExactly(
                        equalTo(EXCEPTION_TYPE, SSLHandshakeException.class.getCanonicalName()),
                        satisfies(EXCEPTION_MESSAGE, s -> s.isNotEmpty()),
                        satisfies(EXCEPTION_STACKTRACE, s -> s.isNotEmpty())));
  }
}
