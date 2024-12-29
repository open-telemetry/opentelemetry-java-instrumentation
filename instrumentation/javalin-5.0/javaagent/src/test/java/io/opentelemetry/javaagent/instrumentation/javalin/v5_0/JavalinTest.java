/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.javalin.v5_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ClientAttributes.CLIENT_ADDRESS;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_ROUTE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_PATH;
import static io.opentelemetry.semconv.UrlAttributes.URL_SCHEME;
import static io.opentelemetry.semconv.UserAgentAttributes.USER_AGENT_ORIGINAL;

import io.javalin.Javalin;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class JavalinTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static Javalin app;
  private static int port;
  private static WebClient client;

  @BeforeAll
  static void setup() {
    port = PortUtils.findOpenPort();
    app = TestJavalinJavaApplication.initJavalin(port);
    client = WebClient.of("http://localhost:" + port);
  }

  @AfterAll
  static void cleanup() {
    app.stop();
  }

  @Test
  void testSpanNameAndHttpRouteSpanWithPathParamResponseSuccessful() {
    String id = "123";
    AggregatedHttpResponse response = client.get("/param/" + id).aggregate().join();
    String content = response.contentUtf8();

    assertThat(content).isEqualTo(id);
    assertThat(response.status().code()).isEqualTo(200);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET /param/{id}")
                        .hasKind(SpanKind.SERVER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(URL_SCHEME, "http"),
                            equalTo(URL_PATH, "/param/" + id),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                            satisfies(USER_AGENT_ORIGINAL, val -> val.isInstanceOf(String.class)),
                            equalTo(HTTP_ROUTE, "/param/{id}"),
                            equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(SERVER_PORT, port),
                            equalTo(CLIENT_ADDRESS, "127.0.0.1"),
                            equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"),
                            satisfies(NETWORK_PEER_PORT, val -> val.isInstanceOf(Long.class)))));
  }

  @Test
  void testSpanNameAndHttpRouteSpanResponseError() {
    client.get("/error").aggregate().join();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET /error")
                        .hasKind(SpanKind.SERVER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(URL_SCHEME, "http"),
                            equalTo(URL_PATH, "/error"),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 500),
                            satisfies(USER_AGENT_ORIGINAL, val -> val.isInstanceOf(String.class)),
                            equalTo(HTTP_ROUTE, "/error"),
                            equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(SERVER_PORT, port),
                            equalTo(ERROR_TYPE, "500"),
                            equalTo(CLIENT_ADDRESS, "127.0.0.1"),
                            equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"),
                            satisfies(NETWORK_PEER_PORT, val -> val.isInstanceOf(Long.class)))));
  }

  @Test
  public void testSpanNameAndHttpRouteSpanAsyncRouteResponseSuccessful() {
    AggregatedHttpResponse response = client.get("/async").aggregate().join();

    assertThat(response.status().code()).isEqualTo(200);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET /async")
                        .hasKind(SpanKind.SERVER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(URL_SCHEME, "http"),
                            equalTo(URL_PATH, "/async"),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                            satisfies(USER_AGENT_ORIGINAL, val -> val.isInstanceOf(String.class)),
                            equalTo(HTTP_ROUTE, "/async"),
                            equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(SERVER_PORT, port),
                            equalTo(CLIENT_ADDRESS, "127.0.0.1"),
                            equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"),
                            satisfies(NETWORK_PEER_PORT, val -> val.isInstanceOf(Long.class)))));
  }

  @Test
  void testHttpRouteMetricWithPathParamResponseSuccessful() {
    String id = "123";
    AggregatedHttpResponse response = client.get("/param/" + id).aggregate().join();
    String content = response.contentUtf8();
    String instrumentation = "io.opentelemetry.jetty-11.0";

    assertThat(content).isEqualTo(id);
    assertThat(response.status().code()).isEqualTo(200);
    testing.waitAndAssertMetrics(
        instrumentation,
        "http.server.request.duration",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasHistogramSatisfying(
                            histogram ->
                                histogram.hasPointsSatisfying(
                                    point -> point.hasAttribute(HTTP_ROUTE, "/param/{id}")))));
  }
}
