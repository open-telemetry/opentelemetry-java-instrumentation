/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
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

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerUsingTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

class RestCamelTest extends AbstractHttpServerUsingTest<ConfigurableApplicationContext> {

  @RegisterExtension
  public static final InstrumentationExtension testing =
      HttpServerInstrumentationExtension.forAgent();

  private ConfigurableApplicationContext appContext;

  @Override
  protected ConfigurableApplicationContext setupServer() {
    SpringApplication app = new SpringApplication(RestConfig.class);
    app.setDefaultProperties(ImmutableMap.of("restServer.port", port));
    appContext = app.run();
    return appContext;
  }

  @Override
  protected void stopServer(ConfigurableApplicationContext ctx) {
    ctx.close();
  }

  @Override
  protected String getContextPath() {
    return "";
  }

  @BeforeAll
  protected void setUp() {
    startServer();
  }

  @AfterAll
  protected void cleanUp() {
    cleanupServer();
  }

  @Test
  void restComponentServerAndClientCallWithJettyBackend() {
    CamelContext camelContext = appContext.getBean(CamelContext.class);
    ProducerTemplate template = camelContext.createProducerTemplate();

    // run client and server in separate threads to simulate "real" rest client/server call
    new Thread(
            () ->
                template.sendBodyAndHeaders(
                    "direct:start",
                    null,
                    ImmutableMap.of("module", "firstModule", "unitId", "unitOne")))
        .start();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("start")
                        .hasKind(SpanKind.INTERNAL)
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("camel.uri"), "direct://start")),
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("camel.uri"),
                                "rest://get:api/%7Bmodule%7D/unit/%7BunitId%7D"),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200L)),
                span ->
                    span.hasName("GET /api/{module}/unit/{unitId}")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(URL_SCHEME, "http"),
                            equalTo(URL_PATH, "/api/firstModule/unit/unitOne"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200L),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_ROUTE, "/api/{module}/unit/{unitId}"),
                            equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(SERVER_PORT, Long.valueOf(port)),
                            equalTo(CLIENT_ADDRESS, "127.0.0.1"),
                            equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"),
                            satisfies(USER_AGENT_ORIGINAL, val -> val.isInstanceOf(String.class)),
                            satisfies(NETWORK_PEER_PORT, val -> val.isInstanceOf(Long.class))),
                span ->
                    span.hasName("GET /api/{module}/unit/{unitId}")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(2))
                        .hasAttributesSatisfyingExactly(
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(
                                URL_FULL,
                                "http://localhost:" + port + "/api/firstModule/unit/unitOne"),
                            satisfies(
                                stringKey("camel.uri"), val -> val.isInstanceOf(String.class))),
                span ->
                    span.hasName("moduleUnit")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(3))
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("camel.uri"), "direct://moduleUnit"))));
  }
}
