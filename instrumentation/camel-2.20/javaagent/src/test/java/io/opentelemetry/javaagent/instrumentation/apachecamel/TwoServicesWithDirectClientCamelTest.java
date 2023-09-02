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
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerUsingTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.semconv.SemanticAttributes;
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

class TwoServicesWithDirectClientCamelTest
    extends AbstractHttpServerUsingTest<ConfigurableApplicationContext> {
  @RegisterExtension
  public static final InstrumentationExtension testing =
      HttpServerInstrumentationExtension.forAgent();

  private static CamelContext clientContext;

  private static Integer portOne;

  private static Integer portTwo;

  @Override
  protected ConfigurableApplicationContext setupServer() {
    portOne = port;
    portTwo = PortUtils.findOpenPort();
    SpringApplication app = new SpringApplication(TwoServicesConfig.class);
    app.setDefaultProperties(
        ImmutableMap.of("service.one.port", portOne, "service.two.port", portTwo));
    return app.run();
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

  void createAndStartClient() throws Exception {
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
  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  void twoCamelServiceSpans() throws Exception {
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
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("camel.uri"), "direct://input")),
                span ->
                    span.hasName("POST")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.HTTP_METHOD, "POST"),
                            equalTo(
                                SemanticAttributes.HTTP_URL,
                                "http://localhost:" + portOne + "/serviceOne"),
                            equalTo(SemanticAttributes.HTTP_STATUS_CODE, 200L),
                            equalTo(
                                stringKey("camel.uri"),
                                "http://localhost:" + portOne + "/serviceOne")),
                span ->
                    span.hasName("POST /serviceOne")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.HTTP_METHOD, "POST"),
                            equalTo(
                                SemanticAttributes.HTTP_URL,
                                "http://localhost:" + portOne + "/serviceOne"),
                            equalTo(SemanticAttributes.HTTP_STATUS_CODE, 200L),
                            equalTo(
                                stringKey("camel.uri"),
                                "http://0.0.0.0:" + portOne + "/serviceOne")),
                span ->
                    span.hasName("POST")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(2))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.HTTP_METHOD, "POST"),
                            equalTo(
                                SemanticAttributes.HTTP_URL,
                                "http://127.0.0.1:" + portTwo + "/serviceTwo"),
                            equalTo(SemanticAttributes.HTTP_STATUS_CODE, 200L),
                            equalTo(
                                stringKey("camel.uri"),
                                "http://127.0.0.1:" + portTwo + "/serviceTwo")),
                span ->
                    span.hasName("POST /serviceTwo")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(3))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.HTTP_METHOD, "POST"),
                            equalTo(SemanticAttributes.HTTP_STATUS_CODE, 200L),
                            equalTo(SemanticAttributes.HTTP_SCHEME, "http"),
                            equalTo(SemanticAttributes.HTTP_TARGET, "/serviceTwo"),
                            equalTo(
                                SemanticAttributes.USER_AGENT_ORIGINAL,
                                "Jakarta Commons-HttpClient/3.1"),
                            equalTo(SemanticAttributes.HTTP_ROUTE, "/serviceTwo"),
                            equalTo(SemanticAttributes.NET_PROTOCOL_NAME, "http"),
                            equalTo(SemanticAttributes.NET_PROTOCOL_VERSION, "1.1"),
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
                        .hasParent(trace.getSpan(4))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.HTTP_METHOD, "POST"),
                            equalTo(
                                SemanticAttributes.HTTP_URL,
                                "http://127.0.0.1:" + portTwo + "/serviceTwo"),
                            equalTo(
                                stringKey("camel.uri"),
                                "jetty:http://0.0.0.0:" + portTwo + "/serviceTwo?arg=value"))));
  }
}
