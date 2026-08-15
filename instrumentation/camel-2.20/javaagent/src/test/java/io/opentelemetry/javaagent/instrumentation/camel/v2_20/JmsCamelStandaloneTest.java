/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static io.opentelemetry.javaagent.instrumentation.camel.v2_20.CamelMessagingMetricsAssertions.assertSendAndProcessMetrics;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.CountDownLatch;
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
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

@EnabledIfSystemProperty(named = "testNoLowerMessaging", matches = "true")
class JmsCamelStandaloneTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static final CountDownLatch received = new CountDownLatch(1);
  private static CamelContext camelContext;

  @BeforeAll
  static void setUp() throws Exception {
    BrokerService broker = new BrokerService();
    broker.setBrokerName("standalone");
    broker.setPersistent(false);
    broker.setUseJmx(false);
    broker.addConnector("vm://standalone");
    broker.start();
    cleanup.deferAfterAll(broker::stop);

    camelContext = new DefaultCamelContext();
    ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://standalone");
    camelContext.addComponent("jms", JmsComponent.jmsComponentAutoAcknowledge(connectionFactory));
    camelContext.addRoutes(
        new RouteBuilder() {
          @Override
          public void configure() {
            from("direct:input").to("jms:queue:standalone");
            from("jms:queue:standalone").process(exchange -> received.countDown());
          }
        });
    camelContext.start();
    cleanup.deferAfterAll(camelContext::stop);
  }

  @Test
  void camelOwnsMetricsWithoutLowerMessagingInstrumentation() throws Exception {
    ProducerTemplate template = camelContext.createProducerTemplate();
    template.sendBody("direct:input", "test message");

    assertThat(received.await(1, MINUTES)).isTrue();
    assertSendAndProcessMetrics(testing, "jms", "standalone");
  }
}
