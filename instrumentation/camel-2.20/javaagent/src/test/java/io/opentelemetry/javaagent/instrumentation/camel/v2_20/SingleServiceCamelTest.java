/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.javaagent.instrumentation.camel.v2_20.ExperimentalTest.experimental;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerUsingTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import java.net.URI;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

class SingleServiceCamelTest extends AbstractHttpServerUsingTest<ConfigurableApplicationContext> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  @Override
  protected ConfigurableApplicationContext setupServer() {
    SpringApplication app = new SpringApplication(SingleServiceConfig.class);
    app.setDefaultProperties(ImmutableMap.of("camelService.port", port));
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

  @Test
  void singleCamelServiceSpan() {
    URI h1RequestUrl = h1Address.resolve("/camelService");
    URI requestUrl = address.resolve("/camelService");

    client.post(h1RequestUrl.toString(), "testContent").aggregate().join();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("POST /camelService")
                        .hasKind(SpanKind.SERVER)
                        .hasAttributesSatisfyingExactly(
                            equalTo(HTTP_REQUEST_METHOD, "POST"),
                            equalTo(URL_FULL, requestUrl.toString()),
                            equalTo(
                                stringKey("camel.uri"),
                                experimental(
                                    requestUrl.toString().replace("localhost", "0.0.0.0"))))));
  }

  @Test
  void sensitiveQueryParametersAreRedacted() throws Exception {
    CamelContext clientContext = new DefaultCamelContext();
    clientContext.addRoutes(
        new RouteBuilder() {
          @Override
          public void configure() {
            from("direct:input").to("http://localhost:" + port + "/camelService?sig=secret");
          }
        });
    clientContext.start();
    try {
      clientContext.createProducerTemplate().sendBody("direct:input", "testContent");

      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("input").hasKind(SpanKind.INTERNAL),
                  span ->
                      span.hasName("GET")
                          .hasKind(SpanKind.CLIENT)
                          .hasAttribute(
                              URL_FULL, "http://localhost:" + port + "/camelService?sig=REDACTED"),
                  span -> span.hasName("GET /camelService").hasKind(SpanKind.SERVER)));
    } finally {
      clientContext.stop();
    }
  }

  @Test
  void userInfoIsRedacted() throws Exception {
    CamelContext clientContext = new DefaultCamelContext();
    clientContext.addRoutes(
        new RouteBuilder() {
          @Override
          public void configure() {
            from("direct:userInfoInput")
                .to("http://user:secret@localhost:" + port + "/camelService");
          }
        });
    clientContext.start();
    try {
      clientContext.createProducerTemplate().sendBody("direct:userInfoInput", "testContent");

      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("userInfoInput").hasKind(SpanKind.INTERNAL),
                  span ->
                      span.hasName("POST")
                          .hasKind(SpanKind.CLIENT)
                          .hasAttribute(
                              URL_FULL,
                              "http://REDACTED:REDACTED@localhost:" + port + "/camelService"),
                  span -> span.hasName("POST /camelService").hasKind(SpanKind.SERVER)));
    } finally {
      clientContext.stop();
    }
  }
}
