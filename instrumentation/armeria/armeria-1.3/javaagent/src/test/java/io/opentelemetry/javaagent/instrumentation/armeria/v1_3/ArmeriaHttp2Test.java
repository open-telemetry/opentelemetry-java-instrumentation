/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.armeria.v1_3;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static org.assertj.core.api.Assertions.assertThat;

import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.testing.junit5.server.ServerExtension;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.semconv.ClientAttributes;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.semconv.UserAgentAttributes;
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
                            equalTo(UrlAttributes.URL_FULL, server2.httpUri() + "/"),
                            equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 200),
                            equalTo(NetworkAttributes.NETWORK_PROTOCOL_VERSION, "2"),
                            equalTo(ServerAttributes.SERVER_ADDRESS, "127.0.0.1"),
                            equalTo(ServerAttributes.SERVER_PORT, server2.httpPort()),
                            equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, "127.0.0.1"),
                            satisfies(
                                NetworkAttributes.NETWORK_PEER_PORT,
                                val -> val.isInstanceOf(Long.class))),
                span ->
                    span.hasName("GET /")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(UrlAttributes.URL_SCHEME, "http"),
                            equalTo(UrlAttributes.URL_PATH, "/"),
                            equalTo(HttpAttributes.HTTP_ROUTE, "/"),
                            equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 200),
                            equalTo(NetworkAttributes.NETWORK_PROTOCOL_VERSION, "2"),
                            equalTo(ClientAttributes.CLIENT_ADDRESS, "127.0.0.1"),
                            equalTo(ServerAttributes.SERVER_ADDRESS, "127.0.0.1"),
                            equalTo(ServerAttributes.SERVER_PORT, server2.httpPort()),
                            satisfies(
                                UserAgentAttributes.USER_AGENT_ORIGINAL,
                                val -> val.isInstanceOf(String.class)),
                            equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, "127.0.0.1"),
                            satisfies(
                                NetworkAttributes.NETWORK_PEER_PORT,
                                val -> val.isInstanceOf(Long.class))),
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(UrlAttributes.URL_FULL, server1.httpUri() + "/"),
                            equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 200),
                            equalTo(NetworkAttributes.NETWORK_PROTOCOL_VERSION, "2"),
                            equalTo(ServerAttributes.SERVER_ADDRESS, "127.0.0.1"),
                            equalTo(ServerAttributes.SERVER_PORT, server1.httpPort()),
                            equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, "127.0.0.1"),
                            satisfies(
                                NetworkAttributes.NETWORK_PEER_PORT,
                                val -> val.isInstanceOf(Long.class))),
                span ->
                    span.hasName("GET /")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(2))
                        .hasAttributesSatisfyingExactly(
                            equalTo(UrlAttributes.URL_SCHEME, "http"),
                            equalTo(UrlAttributes.URL_PATH, "/"),
                            equalTo(HttpAttributes.HTTP_ROUTE, "/"),
                            equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 200),
                            equalTo(NetworkAttributes.NETWORK_PROTOCOL_VERSION, "2"),
                            equalTo(ClientAttributes.CLIENT_ADDRESS, "127.0.0.1"),
                            equalTo(ServerAttributes.SERVER_ADDRESS, "127.0.0.1"),
                            equalTo(ServerAttributes.SERVER_PORT, server1.httpPort()),
                            satisfies(
                                UserAgentAttributes.USER_AGENT_ORIGINAL,
                                val -> val.isInstanceOf(String.class)),
                            equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, "127.0.0.1"),
                            satisfies(
                                NetworkAttributes.NETWORK_PEER_PORT,
                                val -> val.isInstanceOf(Long.class)))));
  }
}
