/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

class MulticastDirectCamelTest {

  @RegisterExtension
  public static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static ConfigurableApplicationContext server;

  @BeforeAll
  static void setUp() {
    SpringApplication app = new SpringApplication(MulticastConfig.class);
    server = app.run();
  }

  @AfterAll
  static void cleanUp() {
    if (server != null) {
      server.close();
      server = null;
    }
  }

  @Test
  void parallelMulticastToTwoChildServices() {
    CamelContext camelContext = server.getBean(CamelContext.class);
    ProducerTemplate template = camelContext.createProducerTemplate();

    template.sendBody("direct:input", "Example request");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactlyInAnyOrder(
                span ->
                    span.hasName("input")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttribute(stringKey("camel.uri"), "direct://input"),
                span ->
                    span.hasName("first")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttribute(stringKey("camel.uri"), "direct://first"),
                span ->
                    span.hasName("second")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttribute(stringKey("camel.uri"), "direct://second")));
  }
}
