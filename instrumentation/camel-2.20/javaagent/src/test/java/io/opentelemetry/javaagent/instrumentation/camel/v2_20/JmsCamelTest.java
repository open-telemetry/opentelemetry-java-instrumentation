/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.javaagent.instrumentation.camel.v2_20.ExperimentalTest.experimental;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import javax.jms.ConnectionFactory;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class JmsCamelTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static BrokerService broker;
  private static CamelContext camelContext;

  @BeforeAll
  static void setUp() throws Exception {
    broker = new BrokerService();
    broker.setPersistent(false);
    broker.setUseJmx(false);
    broker.addConnector("vm://localhost");
    broker.start();
    cleanup.deferAfterAll(broker::stop);

    camelContext = new DefaultCamelContext();
    ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost");
    camelContext.addComponent("jms", JmsComponent.jmsComponentAutoAcknowledge(connectionFactory));

    camelContext.addRoutes(
        new RouteBuilder() {
          @Override
          public void configure() {
            from("direct:input").to("jms:queue:testQueue");
            from("jms:queue:testQueue").to("mock:result");
          }
        });

    camelContext.start();
    cleanup.deferAfterAll(camelContext::stop);
    testing.clearData();
  }

  @Test
  void testJmsProducerAndConsumer() {
    ProducerTemplate template = camelContext.createProducerTemplate();
    template.sendBody("direct:input", "test message");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("input").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("queue:testQueue")
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_DESTINATION_NAME, "queue:testQueue"),
                            equalTo(stringKey("camel.uri"), experimental("jms://queue:testQueue"))),
                span ->
                    span.hasName("queue:testQueue")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_DESTINATION_NAME, "queue:testQueue"),
                            equalTo(stringKey("camel.uri"), experimental("jms://queue:testQueue")),
                            satisfies(MESSAGING_MESSAGE_ID, val -> val.isInstanceOf(String.class))),
                span -> span.hasName("mock").hasKind(SpanKind.CLIENT).hasParent(trace.getSpan(2))));
  }
}
