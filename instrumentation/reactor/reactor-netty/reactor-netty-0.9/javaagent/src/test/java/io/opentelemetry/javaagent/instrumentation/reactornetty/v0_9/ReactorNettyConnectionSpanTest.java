/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v0_9;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.api.trace.SpanKind.SERVER;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestServer;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import org.assertj.core.api.AbstractLongAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.netty.http.client.HttpClient;

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
                            equalTo(ServerAttributes.SERVER_ADDRESS, "localhost"),
                            equalTo(ServerAttributes.SERVER_PORT, server.httpPort())),
                span ->
                    span.hasName("CONNECT")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(NetworkAttributes.NETWORK_TRANSPORT, "tcp"),
                            equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                            equalTo(ServerAttributes.SERVER_ADDRESS, "localhost"),
                            equalTo(ServerAttributes.SERVER_PORT, server.httpPort()),
                            equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, "127.0.0.1"),
                            satisfies(
                                NetworkAttributes.NETWORK_PEER_PORT,
                                AbstractLongAssert::isNotNegative)),
                span -> span.hasName("GET").hasKind(CLIENT).hasParent(trace.getSpan(0)),
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
            trace.hasSpansSatisfyingExactlyInAnyOrder(
                span ->
                    span.hasName("parent")
                        .hasKind(INTERNAL)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(thrown),
                span ->
                    span.hasName("RESOLVE")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(ServerAttributes.SERVER_ADDRESS, "localhost"),
                            equalTo(ServerAttributes.SERVER_PORT, PortUtils.UNUSABLE_PORT)),
                span ->
                    span.hasName("CONNECT")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error())
                        .hasException(connectException)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NetworkAttributes.NETWORK_TRANSPORT, "tcp"),
                            satisfies(
                                NetworkAttributes.NETWORK_TYPE, val -> val.isIn(null, "ipv4")),
                            equalTo(ServerAttributes.SERVER_ADDRESS, "localhost"),
                            equalTo(ServerAttributes.SERVER_PORT, PortUtils.UNUSABLE_PORT),
                            satisfies(
                                NetworkAttributes.NETWORK_PEER_ADDRESS,
                                val -> val.isIn(null, "127.0.0.1")),
                            satisfies(
                                NetworkAttributes.NETWORK_PEER_PORT,
                                val -> val.isIn(null, (long) PortUtils.UNUSABLE_PORT)))));
  }
}
