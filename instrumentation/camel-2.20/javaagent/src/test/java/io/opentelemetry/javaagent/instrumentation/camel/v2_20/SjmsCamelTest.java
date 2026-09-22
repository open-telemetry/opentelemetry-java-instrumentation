/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
import org.apache.camel.component.sjms.SjmsComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SjmsCamelTest {

  private static final String CAMEL_INSTRUMENTATION_NAME = "io.opentelemetry.camel-2.20";
  private static final String JMS_INSTRUMENTATION_NAME = "io.opentelemetry.jms-1.1";
  private static final boolean TEST_CAMEL_DISABLED = Boolean.getBoolean("testCamelDisabled");

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static final CountDownLatch received = new CountDownLatch(2);
  private static CamelContext camelContext;

  @BeforeAll
  static void setUp() throws Exception {
    BrokerService broker = new BrokerService();
    broker.setBrokerName("sjms");
    broker.setPersistent(false);
    broker.setUseJmx(false);
    broker.addConnector("vm://sjms");
    broker.start();
    cleanup.deferAfterAll(broker::stop);

    camelContext = new DefaultCamelContext();
    ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://sjms");
    SjmsComponent component = new SjmsComponent();
    component.setConnectionFactory(connectionFactory);
    camelContext.addComponent("sjms", component);
    camelContext.addRoutes(
        new RouteBuilder() {
          @Override
          public void configure() {
            from("direct:input").to("sjms:queue:testQueue");
            from("sjms:queue:testQueue").process(exchange -> received.countDown());
          }
        });
    camelContext.start();
    cleanup.deferAfterAll(camelContext::stop);
  }

  @Test
  void recordsTwoProcessOperationsThroughSjms() throws Exception {
    assumeTrue(emitStableMessagingSemconv());

    ProducerTemplate template = camelContext.createProducerTemplate();
    template.sendBody("direct:input", "test message");
    template.sendBody("direct:input", "test message");

    assertThat(received.await(1, MINUTES)).isTrue();
    String expectedInstrumentationName =
        TEST_CAMEL_DISABLED ? JMS_INSTRUMENTATION_NAME : CAMEL_INSTRUMENTATION_NAME;
    String unexpectedInstrumentationName =
        TEST_CAMEL_DISABLED ? CAMEL_INSTRUMENTATION_NAME : JMS_INSTRUMENTATION_NAME;
    assertProcessDuration(expectedInstrumentationName, 2);
    assertNoProcessDuration(unexpectedInstrumentationName);
  }

  private static void assertProcessDuration(String instrumentationName, long expectedCount) {
    testing.waitAndAssertMetrics(
        instrumentationName,
        "messaging.process.duration",
        metrics ->
            metrics.satisfiesExactly(
                metric ->
                    assertThat(metric.getHistogramData().getPoints())
                        .singleElement()
                        .satisfies(
                            point -> assertThat(point.getCount()).isEqualTo(expectedCount))));
  }

  private static void assertNoProcessDuration(String instrumentationName) {
    assertThat(testing.metrics())
        .noneMatch(
            metric ->
                instrumentationName.equals(metric.getInstrumentationScopeInfo().getName())
                    && metric.getName().equals("messaging.process.duration"));
  }
}
