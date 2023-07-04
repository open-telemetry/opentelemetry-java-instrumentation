/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

class RestCamelTest {

  private static final Logger logger = LoggerFactory.getLogger(RestCamelTest.class);

  @RegisterExtension
  public static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static ConfigurableApplicationContext server;

  private static Integer port;

  @BeforeAll
  static void setUp() {
    port = PortUtils.findOpenPort();
    SpringApplication app = new SpringApplication(RestConfig.class);
    app.setDefaultProperties(ImmutableMap.of("restServer.port", port));
    server = app.run();
    logger.info("http server started at: http://localhost:{}/", port);
  }

  @AfterAll
  static void cleanUp() {
    if (server != null) {
      server.close();
      server = null;
    }
  }

  @Test
  void restComponentServerAndClientCallWithJettyBackend() {
    CamelContext camelContext = server.getBean(CamelContext.class);
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
                        .hasAttributesSatisfying(equalTo(stringKey("camel.uri"), "direct://start")),
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfying(
                            equalTo(
                                stringKey("camel.uri"),
                                "rest://get:api/%7Bmodule%7D/unit/%7BunitId%7D"),
                            equalTo(SemanticAttributes.HTTP_METHOD, "GET"),
                            equalTo(SemanticAttributes.HTTP_STATUS_CODE, 200L)),
                span ->
                    span.hasName("GET /api/{module}/unit/{unitId}")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfying(
                            equalTo(SemanticAttributes.HTTP_SCHEME, "http"),
                            equalTo(
                                SemanticAttributes.HTTP_TARGET, "/api/firstModule/unit/unitOne"),
                            equalTo(SemanticAttributes.HTTP_STATUS_CODE, 200L),
                            equalTo(SemanticAttributes.HTTP_METHOD, "GET"),
                            equalTo(SemanticAttributes.HTTP_ROUTE, "/api/{module}/unit/{unitId}"),
                            equalTo(stringKey("net.protocol.name"), "http"),
                            equalTo(stringKey("net.protocol.version"), "1.1"),
                            equalTo(SemanticAttributes.NET_HOST_NAME, "localhost"),
                            equalTo(SemanticAttributes.NET_HOST_PORT, Long.valueOf(port)),
                            equalTo(SemanticAttributes.NET_SOCK_PEER_ADDR, "127.0.0.1"),
                            equalTo(SemanticAttributes.NET_SOCK_HOST_ADDR, "127.0.0.1"),
                            satisfies(
                                SemanticAttributes.USER_AGENT_ORIGINAL,
                                val -> val.isInstanceOf(String.class)),
                            satisfies(
                                SemanticAttributes.NET_SOCK_PEER_PORT,
                                val -> val.isInstanceOf(Long.class))),
                span ->
                    span.hasName("GET /api/{module}/unit/{unitId}")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(2))
                        .hasAttributesSatisfying(
                            equalTo(SemanticAttributes.HTTP_METHOD, "GET"),
                            equalTo(
                                SemanticAttributes.HTTP_URL,
                                "http://localhost:" + port + "/api/firstModule/unit/unitOne"),
                            satisfies(
                                stringKey("camel.uri"), val -> val.isInstanceOf(String.class))),
                span ->
                    span.hasName("moduleUnit")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(3))
                        .hasAttribute(stringKey("camel.uri"), "direct://moduleUnit")));
  }
}
