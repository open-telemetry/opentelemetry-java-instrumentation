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
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.test.server.http.RequestContextGetter;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.testing.internal.armeria.common.HttpData;
import io.opentelemetry.testing.internal.armeria.common.HttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpStatus;
import io.opentelemetry.testing.internal.armeria.common.ResponseHeaders;
import io.opentelemetry.testing.internal.armeria.common.ResponseHeadersBuilder;
import io.opentelemetry.testing.internal.armeria.server.ServerBuilder;
import io.opentelemetry.testing.internal.armeria.testing.junit5.server.ServerExtension;
import org.assertj.core.api.AbstractLongAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.netty.http.client.HttpClient;

class ReactorNettyBaseUrlOnlyTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  static ServerExtension server;

  @BeforeAll
  static void setUp() {
    server =
        new ServerExtension() {
          @Override
          protected void configure(ServerBuilder sb) {
            sb.service(
                "/base/",
                (ctx, req) -> {
                  ResponseHeadersBuilder headers = ResponseHeaders.builder(HttpStatus.OK);

                  SpanBuilder span =
                      GlobalOpenTelemetry.getTracer("test")
                          .spanBuilder("test-http-server")
                          .setSpanKind(SERVER)
                          .setParent(
                              GlobalOpenTelemetry.getPropagators()
                                  .getTextMapPropagator()
                                  .extract(Context.current(), ctx, RequestContextGetter.INSTANCE));

                  span.startSpan().end();

                  return HttpResponse.of(headers.build(), HttpData.ofAscii("Hello."));
                });
          }
        };
    server.start();
  }

  @AfterAll
  static void tearDown() {
    server.stop();
  }

  @Test
  void testSuccessfulRequest() {
    HttpClient httpClient = HttpClient.create();
    String uri = "http://localhost:" + server.httpPort() + "/base";

    int responseCode =
        testing.runWithSpan(
            "parent",
            () ->
                httpClient
                    .baseUrl(uri)
                    .headers(h -> h.set("User-Agent", USER_AGENT))
                    .get()
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
                    span.hasName("GET")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(URL_FULL, uri + "/"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                            equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(SERVER_PORT, server.httpPort()),
                            equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"),
                            satisfies(NETWORK_PEER_PORT, AbstractLongAssert::isNotNegative)),
                span ->
                    span.hasName("test-http-server").hasKind(SERVER).hasParent(trace.getSpan(1))));
  }
}
