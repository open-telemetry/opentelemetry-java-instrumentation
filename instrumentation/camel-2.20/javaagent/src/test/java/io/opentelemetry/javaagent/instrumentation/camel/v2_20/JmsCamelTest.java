/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.javaagent.instrumentation.camel.v2_20.CamelMessagingMetricsAssertions.assertSendAndProcessMetrics;
import static io.opentelemetry.javaagent.instrumentation.camel.v2_20.ExperimentalTest.experimental;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.LinkData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
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
  private static final CountDownLatch errorProcessed = new CountDownLatch(1);

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
            from("direct:errorInput").to("jms:queue:errorQueue");
            from("jms:queue:errorQueue")
                .process(
                    exchange -> {
                      errorProcessed.countDown();
                      throw new IllegalStateException("test");
                    });
          }
        });

    camelContext.start();
    cleanup.deferAfterAll(camelContext::stop);
  }

  @Test
  void testJmsProducerAndConsumer() {
    ProducerTemplate template = camelContext.createProducerTemplate();
    template.sendBody("direct:input", "test message");

    if (emitStableMessagingSemconv()) {
      testing.waitAndAssertSortedTraces(
          Comparator.comparingInt(trace -> trace.size()),
          JmsCamelTest::assertJmsReceiveTrace,
          JmsCamelTest::assertCamelTrace);
    }
    if (!emitStableMessagingSemconv()) {
      testing.waitAndAssertTraces(JmsCamelTest::assertCamelTrace);
    }
    assertSendAndProcessMetrics(testing, "jms", "testQueue");
  }

  @Test
  void failedCamelProcessCountsDeliveredMessage() throws Exception {
    ProducerTemplate template = camelContext.createProducerTemplate();
    template.sendBody("direct:errorInput", "test message");

    assertThat(errorProcessed.await(1, MINUTES)).isTrue();
    testing.waitForTraces(emitStableMessagingSemconv() ? 2 : 1);
    assertSendAndProcessMetrics(
        testing, "jms", "errorQueue", IllegalStateException.class.getName());
  }

  private static void assertJmsReceiveTrace(TraceAssert trace) {
    trace.hasSpansSatisfyingExactly(
        span ->
            span.hasName("receive testQueue")
                .hasKind(SpanKind.CLIENT)
                .hasNoParent()
                .hasTotalRecordedLinks(1)
                .hasAttributesSatisfyingExactly(
                    equalTo(MESSAGING_SYSTEM, "jms"),
                    equalTo(MESSAGING_DESTINATION_NAME, "testQueue"),
                    equalTo(MESSAGING_OPERATION_NAME, "receive"),
                    equalTo(MESSAGING_OPERATION_TYPE, "receive"),
                    satisfies(MESSAGING_MESSAGE_ID, val -> val.isInstanceOf(String.class))));
  }

  private static void assertCamelTrace(TraceAssert trace) {
    List<Consumer<SpanDataAssert>> assertions = new ArrayList<>();
    assertions.add(span -> span.hasName("input").hasKind(SpanKind.INTERNAL).hasNoParent());
    assertions.add(
        span ->
            span.hasName(emitStableMessagingSemconv() ? "send testQueue" : "queue:testQueue")
                .hasKind(SpanKind.PRODUCER)
                .hasParent(trace.getSpan(0))
                .hasAttributesSatisfyingExactly(
                    equalTo(MESSAGING_SYSTEM, emitStableMessagingSemconv() ? "jms" : null),
                    equalTo(
                        MESSAGING_DESTINATION_NAME,
                        emitStableMessagingSemconv() ? "testQueue" : "queue:testQueue"),
                    equalTo(MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? "send" : null),
                    equalTo(MESSAGING_OPERATION_TYPE, emitStableMessagingSemconv() ? "send" : null),
                    equalTo(stringKey("camel.uri"), experimental("jms://queue:testQueue"))));
    if (!emitStableMessagingSemconv()) {
      assertions.add(
          span ->
              span.hasName("testQueue receive")
                  .hasKind(SpanKind.CONSUMER)
                  .hasParent(trace.getSpan(1))
                  .hasTotalRecordedLinks(0)
                  .hasAttributesSatisfyingExactly(
                      equalTo(MESSAGING_SYSTEM, "jms"),
                      equalTo(MESSAGING_DESTINATION_NAME, "testQueue"),
                      equalTo(stringKey("messaging.operation"), "receive"),
                      satisfies(MESSAGING_MESSAGE_ID, val -> val.isInstanceOf(String.class))));
    }
    int processSpanIndex = assertions.size();
    assertions.add(
        span -> {
          span.hasName(emitStableMessagingSemconv() ? "process testQueue" : "queue:testQueue")
              .hasKind(SpanKind.CONSUMER)
              .hasParent(trace.getSpan(1))
              .hasAttributesSatisfyingExactly(
                  equalTo(MESSAGING_SYSTEM, emitStableMessagingSemconv() ? "jms" : null),
                  equalTo(
                      MESSAGING_DESTINATION_NAME,
                      emitStableMessagingSemconv() ? "testQueue" : "queue:testQueue"),
                  equalTo(
                      MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? "process" : null),
                  equalTo(
                      MESSAGING_OPERATION_TYPE, emitStableMessagingSemconv() ? "process" : null),
                  equalTo(stringKey("camel.uri"), experimental("jms://queue:testQueue")),
                  satisfies(MESSAGING_MESSAGE_ID, val -> val.isInstanceOf(String.class)));
          if (emitStableMessagingSemconv()) {
            span.hasLinks(LinkData.create(trace.getSpan(1).getSpanContext()));
          }
        });
    assertions.add(
        span ->
            span.hasName("mock")
                .hasKind(SpanKind.CLIENT)
                .hasParent(trace.getSpan(processSpanIndex)));
    trace.hasSpansSatisfyingExactly(assertions);
  }
}
