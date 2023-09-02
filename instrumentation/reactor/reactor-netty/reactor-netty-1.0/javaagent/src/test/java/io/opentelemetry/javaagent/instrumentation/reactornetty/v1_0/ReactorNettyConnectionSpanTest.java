/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.api.trace.SpanKind.SERVER;
import static io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0.AbstractReactorNettyHttpClientTest.USER_AGENT;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.SemanticAttributes.NetTransportValues.IP_TCP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestServer;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.SemanticAttributes;
import org.assertj.core.api.AbstractLongAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.netty.http.client.HttpClient;

@SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
class ReactorNettyConnectionSpanTest {

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
  void testSuccessfulRequest() {
    HttpClient httpClient = HttpClient.create();
    String uri = "http://localhost:" + server.httpPort() + "/success";

    int responseCode =
        testing.runWithSpan(
            "parent",
            () ->
                httpClient
                    .headers(h -> h.set("User-Agent", USER_AGENT))
                    .get()
                    .uri(uri)
                    .responseSingle(
                        (resp, content) -> {
                          // Make sure to consume content since that's when we close the span.
                          return content.map(unused -> resp);
                        })
                    .block()
                    .status()
                    .code());

    assertThat(responseCode).isEqualTo(200);

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
                            equalTo(SemanticAttributes.NET_PEER_PORT, server.httpPort())),
                span ->
                    span.hasName("CONNECT")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.NET_TRANSPORT, IP_TCP),
                            equalTo(SemanticAttributes.NET_PEER_NAME, "localhost"),
                            equalTo(SemanticAttributes.NET_PEER_PORT, server.httpPort()),
                            equalTo(SemanticAttributes.NET_SOCK_PEER_ADDR, "127.0.0.1")),
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
                            equalTo(SemanticAttributes.NET_PEER_PORT, server.httpPort()),
                            equalTo(SemanticAttributes.NET_SOCK_PEER_ADDR, "127.0.0.1")),
                span ->
                    span.hasName("test-http-server").hasKind(SERVER).hasParent(trace.getSpan(3))));
  }

  @Test
  void testFailingRequest() {
    HttpClient httpClient = HttpClient.create();
    String uri = "http://localhost:" + PortUtils.UNUSABLE_PORT;

    Throwable thrown =
        catchThrowable(
            () ->
                testing.runWithSpan(
                    "parent",
                    () ->
                        httpClient
                            .get()
                            .uri(uri)
                            .responseSingle(
                                (resp, content) -> {
                                  // Make sure to consume content since that's when we close the
                                  // span.
                                  return content.map(unused -> resp);
                                })
                            .block()
                            .status()
                            .code()));

    Throwable connectException = thrown.getCause();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
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
                        .hasException(connectException)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.HTTP_METHOD, "GET"),
                            equalTo(SemanticAttributes.HTTP_URL, uri),
                            equalTo(SemanticAttributes.NET_PEER_NAME, "localhost"),
                            equalTo(SemanticAttributes.NET_PEER_PORT, PortUtils.UNUSABLE_PORT)),
                span ->
                    span.hasName("RESOLVE")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.NET_TRANSPORT, IP_TCP),
                            equalTo(SemanticAttributes.NET_PEER_NAME, "localhost"),
                            equalTo(SemanticAttributes.NET_PEER_PORT, PortUtils.UNUSABLE_PORT)),
                span ->
                    span.hasName("CONNECT")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error())
                        .hasException(connectException)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.NET_TRANSPORT, IP_TCP),
                            equalTo(SemanticAttributes.NET_PEER_NAME, "localhost"),
                            equalTo(SemanticAttributes.NET_PEER_PORT, PortUtils.UNUSABLE_PORT),
                            satisfies(
                                SemanticAttributes.NET_SOCK_PEER_ADDR,
                                val -> val.isIn(null, "127.0.0.1")))));
  }
}
