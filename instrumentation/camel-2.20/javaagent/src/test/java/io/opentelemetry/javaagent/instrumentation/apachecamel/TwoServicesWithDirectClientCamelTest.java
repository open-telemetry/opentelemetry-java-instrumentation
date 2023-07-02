/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

public class TwoServicesWithDirectClientCamelTest extends RetryOnAddressAlreadyInUse {
  @RegisterExtension
  public static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static ConfigurableApplicationContext server;

  private static CamelContext clientContext;

  private static Integer portOne;

  private static Integer portTwo;

  @BeforeAll
  public static void setUp() {
    withRetryOnAddressAlreadyInUse(TwoServicesWithDirectClientCamelTest::setUpUnderRetry);
  }

  public static void setUpUnderRetry() {
    portOne = PortUtils.findOpenPort();
    portTwo = PortUtils.findOpenPort();
    SpringApplication app = new SpringApplication(TwoServicesConfig.class);
    app.setDefaultProperties(
        ImmutableMap.of("service.one.port", portOne, "service.two.port", portTwo));
    server = app.run();
  }

  @AfterAll
  public static void cleanUp() {
    if (server != null) {
      server.close();
      server = null;
    }
  }

  public void createAndStartClient() throws Exception {
    clientContext = new DefaultCamelContext();
    clientContext.addRoutes(
        new RouteBuilder() {
          @Override
          public void configure() {
            from("direct:input")
                .log("SENT Client request")
                .to("http://localhost:" + portOne + "/serviceOne")
                .log("RECEIVED Client response");
          }
        });
    clientContext.start();
  }

  @Test
  public void twoCamelServiceSpans() throws Exception {
    createAndStartClient();

    ProducerTemplate template = clientContext.createProducerTemplate();

    template.sendBody("direct:input", "Example request");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("input")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttribute(AttributeKey.stringKey("camel.uri"), "direct://input"),
                span ->
                    span.hasName("POST")
                        .hasKind(SpanKind.CLIENT)
                        .hasParentSpanId(trace.getSpan(0).getSpanId())
                        .hasAttributesSatisfying(
                            equalTo(SemanticAttributes.HTTP_METHOD, "POST"),
                            equalTo(
                                SemanticAttributes.HTTP_URL,
                                "http://localhost:" + portOne + "/serviceOne"),
                            equalTo(SemanticAttributes.HTTP_STATUS_CODE, 200L),
                            equalTo(
                                AttributeKey.stringKey("camel.uri"),
                                "http://localhost:" + portOne + "/serviceOne")),
                span ->
                    span.hasName("POST /serviceOne")
                        .hasKind(SpanKind.SERVER)
                        .hasParentSpanId(trace.getSpan(1).getSpanId())
                        .hasAttributesSatisfying(
                            equalTo(SemanticAttributes.HTTP_METHOD, "POST"),
                            equalTo(
                                SemanticAttributes.HTTP_URL,
                                "http://localhost:" + portOne + "/serviceOne"),
                            equalTo(SemanticAttributes.HTTP_STATUS_CODE, 200L),
                            equalTo(
                                AttributeKey.stringKey("camel.uri"),
                                "http://0.0.0.0:" + portOne + "/serviceOne")),
                span ->
                    span.hasName("POST")
                        .hasKind(SpanKind.CLIENT)
                        .hasParentSpanId(trace.getSpan(2).getSpanId())
                        .hasAttributesSatisfying(
                            equalTo(SemanticAttributes.HTTP_METHOD, "POST"),
                            equalTo(
                                SemanticAttributes.HTTP_URL,
                                "http://127.0.0.1:" + portTwo + "/serviceTwo"),
                            equalTo(SemanticAttributes.HTTP_STATUS_CODE, 200L),
                            equalTo(
                                AttributeKey.stringKey("camel.uri"),
                                "http://127.0.0.1:" + portTwo + "/serviceTwo")),
                span ->
                    span.hasName("POST /serviceTwo")
                        .hasKind(SpanKind.SERVER)
                        .hasParentSpanId(trace.getSpan(3).getSpanId())
                        .hasAttributesSatisfying(
                            equalTo(SemanticAttributes.HTTP_METHOD, "POST"),
                            equalTo(SemanticAttributes.HTTP_STATUS_CODE, 200L),
                            equalTo(SemanticAttributes.HTTP_SCHEME, "http"),
                            equalTo(SemanticAttributes.HTTP_TARGET, "/serviceTwo"),
                            equalTo(
                                SemanticAttributes.USER_AGENT_ORIGINAL,
                                "Jakarta Commons-HttpClient/3.1"),
                            equalTo(SemanticAttributes.HTTP_ROUTE, "/serviceTwo"),
                            equalTo(AttributeKey.stringKey("net.protocol.name"), "http"),
                            equalTo(AttributeKey.stringKey("net.protocol.version"), "1.1"),
                            equalTo(SemanticAttributes.NET_HOST_NAME, "127.0.0.1"),
                            equalTo(SemanticAttributes.NET_HOST_PORT, portTwo),
                            equalTo(SemanticAttributes.NET_SOCK_PEER_ADDR, "127.0.0.1"),
                            satisfies(
                                SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH,
                                val -> val.isInstanceOf(Long.class)),
                            satisfies(
                                SemanticAttributes.NET_SOCK_PEER_PORT,
                                val -> val.isInstanceOf(Long.class))),
                span ->
                    span.hasName("POST /serviceTwo")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParentSpanId(trace.getSpan(4).getSpanId())
                        .hasAttributesSatisfying(
                            equalTo(SemanticAttributes.HTTP_METHOD, "POST"),
                            equalTo(
                                SemanticAttributes.HTTP_URL,
                                "http://127.0.0.1:" + portTwo + "/serviceTwo"),
                            equalTo(
                                AttributeKey.stringKey("camel.uri"),
                                "jetty:http://0.0.0.0:" + portTwo + "/serviceTwo?arg=value"))));
  }
}
