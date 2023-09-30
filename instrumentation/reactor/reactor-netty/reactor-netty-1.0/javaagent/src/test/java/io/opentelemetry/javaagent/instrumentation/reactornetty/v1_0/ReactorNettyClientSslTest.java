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
import static io.opentelemetry.semconv.SemanticAttributes.NetTransportValues.IP_TCP;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.netty.handler.ssl.SslContextBuilder;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestServer;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.SemanticAttributes;
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

@SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
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
                            equalTo(SemanticAttributes.HTTP_METHOD, "GET"),
                            equalTo(SemanticAttributes.HTTP_URL, uri),
                            equalTo(SemanticAttributes.NET_PEER_NAME, "localhost"),
                            equalTo(SemanticAttributes.NET_PEER_PORT, server.httpsPort())),
                span ->
                    span.hasName("RESOLVE")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.NET_TRANSPORT, IP_TCP),
                            equalTo(SemanticAttributes.NET_PEER_NAME, "localhost"),
                            equalTo(SemanticAttributes.NET_PEER_PORT, server.httpsPort())),
                span ->
                    span.hasName("CONNECT")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.NET_TRANSPORT, IP_TCP),
                            equalTo(SemanticAttributes.NET_PEER_NAME, "localhost"),
                            equalTo(SemanticAttributes.NET_PEER_PORT, server.httpsPort()),
                            equalTo(SemanticAttributes.NET_SOCK_PEER_ADDR, "127.0.0.1")),
                span ->
                    span.hasName("SSL handshake")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error())
                        // netty swallows the exception, it doesn't make any sense to hard-code the
                        // message
                        .hasEventsSatisfying(ReactorNettyClientSslTest::isSslHandshakeException)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.NET_TRANSPORT, IP_TCP),
                            equalTo(SemanticAttributes.NET_SOCK_PEER_ADDR, "127.0.0.1"),
                            equalTo(SemanticAttributes.NET_SOCK_PEER_NAME, "localhost"),
                            equalTo(SemanticAttributes.NET_SOCK_PEER_PORT, server.httpsPort()))));
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
                            equalTo(SemanticAttributes.NET_TRANSPORT, IP_TCP),
                            equalTo(SemanticAttributes.NET_PEER_NAME, "localhost"),
                            equalTo(SemanticAttributes.NET_PEER_PORT, server.httpsPort())),
                span ->
                    span.hasName("CONNECT")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.NET_TRANSPORT, IP_TCP),
                            equalTo(SemanticAttributes.NET_PEER_NAME, "localhost"),
                            equalTo(SemanticAttributes.NET_PEER_PORT, server.httpsPort()),
                            equalTo(SemanticAttributes.NET_SOCK_PEER_ADDR, "127.0.0.1")),
                span ->
                    span.hasName("SSL handshake")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.NET_TRANSPORT, IP_TCP),
                            equalTo(SemanticAttributes.NET_SOCK_PEER_ADDR, "127.0.0.1"),
                            equalTo(SemanticAttributes.NET_SOCK_PEER_NAME, "localhost"),
                            equalTo(SemanticAttributes.NET_SOCK_PEER_PORT, server.httpsPort())),
                span ->
                    span.hasName("GET")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.HTTP_METHOD, "GET"),
                            equalTo(SemanticAttributes.HTTP_URL, uri),
                            equalTo(SemanticAttributes.USER_AGENT_ORIGINAL, USER_AGENT),
                            equalTo(SemanticAttributes.NET_PROTOCOL_NAME, "http"),
                            equalTo(SemanticAttributes.NET_PROTOCOL_VERSION, "1.1"),
                            equalTo(SemanticAttributes.HTTP_STATUS_CODE, 200),
                            equalTo(SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH, 0),
                            satisfies(
                                SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH,
                                AbstractLongAssert::isNotNegative),
                            equalTo(SemanticAttributes.NET_PEER_NAME, "localhost"),
                            equalTo(SemanticAttributes.NET_PEER_PORT, server.httpsPort()),
                            equalTo(SemanticAttributes.NET_SOCK_PEER_ADDR, "127.0.0.1")),
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
        .filteredOn(event -> event.getName().equals(SemanticAttributes.EXCEPTION_EVENT_NAME))
        .satisfiesExactly(
            event ->
                assertThat(event)
                    .hasAttributesSatisfyingExactly(
                        equalTo(
                            SemanticAttributes.EXCEPTION_TYPE,
                            SSLHandshakeException.class.getCanonicalName()),
                        satisfies(SemanticAttributes.EXCEPTION_MESSAGE, s -> s.isNotEmpty()),
                        satisfies(SemanticAttributes.EXCEPTION_STACKTRACE, s -> s.isNotEmpty())));
  }
}
