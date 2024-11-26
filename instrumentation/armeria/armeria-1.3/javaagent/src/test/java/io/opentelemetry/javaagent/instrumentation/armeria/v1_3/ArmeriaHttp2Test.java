/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.armeria.v1_3;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ClientAttributes.CLIENT_ADDRESS;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_ROUTE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.UrlAttributes.URL_PATH;
import static io.opentelemetry.semconv.UrlAttributes.URL_SCHEME;
import static io.opentelemetry.semconv.UserAgentAttributes.USER_AGENT_ORIGINAL;
import static org.assertj.core.api.Assertions.assertThat;

import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.testing.junit5.server.ServerExtension;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ArmeriaHttp2Test {
  @RegisterExtension
  static final AgentInstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  static ServerExtension server1 =
      new ServerExtension() {
        @Override
        protected void configure(ServerBuilder sb) {
          sb.service("/", (ctx, req) -> HttpResponse.of("hello"));
        }
      };

  @RegisterExtension
  static ServerExtension server2 =
      new ServerExtension() {
        @Override
        protected void configure(ServerBuilder sb) {
          sb.service("/", (ctx, req) -> createWebClient(server1).get("/"));
        }
      };

  private static WebClient createWebClient(ServerExtension server) {
    return WebClient.builder(server.httpUri()).build();
  }

  @Test
  void testHello() throws Exception {
    // verify that spans are created and context is propagated
    AggregatedHttpResponse result = createWebClient(server2).get("/").aggregate().get();
    assertThat(result.contentAscii()).isEqualTo("hello");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(URL_FULL, server2.httpUri() + "/"),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                            equalTo(NETWORK_PROTOCOL_VERSION, "2"),
                            equalTo(SERVER_ADDRESS, "127.0.0.1"),
                            equalTo(SERVER_PORT, server2.httpPort()),
                            equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"),
                            satisfies(NETWORK_PEER_PORT, val -> val.isInstanceOf(Long.class))),
                span ->
                    span.hasName("GET /")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(URL_SCHEME, "http"),
                            equalTo(URL_PATH, "/"),
                            equalTo(HTTP_ROUTE, "/"),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                            equalTo(NETWORK_PROTOCOL_VERSION, "2"),
                            equalTo(CLIENT_ADDRESS, "127.0.0.1"),
                            equalTo(SERVER_ADDRESS, "127.0.0.1"),
                            equalTo(SERVER_PORT, server2.httpPort()),
                            satisfies(USER_AGENT_ORIGINAL, val -> val.isInstanceOf(String.class)),
                            equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"),
                            satisfies(NETWORK_PEER_PORT, val -> val.isInstanceOf(Long.class))),
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(URL_FULL, server1.httpUri() + "/"),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                            equalTo(NETWORK_PROTOCOL_VERSION, "2"),
                            equalTo(SERVER_ADDRESS, "127.0.0.1"),
                            equalTo(SERVER_PORT, server1.httpPort()),
                            equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"),
                            satisfies(NETWORK_PEER_PORT, val -> val.isInstanceOf(Long.class))),
                span ->
                    span.hasName("GET /")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(2))
                        .hasAttributesSatisfyingExactly(
                            equalTo(URL_SCHEME, "http"),
                            equalTo(URL_PATH, "/"),
                            equalTo(HTTP_ROUTE, "/"),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                            equalTo(NETWORK_PROTOCOL_VERSION, "2"),
                            equalTo(CLIENT_ADDRESS, "127.0.0.1"),
                            equalTo(SERVER_ADDRESS, "127.0.0.1"),
                            equalTo(SERVER_PORT, server1.httpPort()),
                            satisfies(USER_AGENT_ORIGINAL, val -> val.isInstanceOf(String.class)),
                            equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"),
                            satisfies(NETWORK_PEER_PORT, val -> val.isInstanceOf(Long.class)))));
  }
}
