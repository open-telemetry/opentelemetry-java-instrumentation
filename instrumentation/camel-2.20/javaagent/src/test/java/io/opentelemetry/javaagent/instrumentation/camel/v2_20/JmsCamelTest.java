/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.asRemote;
import static io.opentelemetry.javaagent.instrumentation.camel.v2_20.CamelMessagingMetricsAssertions.assertSendAndProcessMetrics;
import static io.opentelemetry.javaagent.instrumentation.camel.v2_20.ExperimentalTest.experimental;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_TEMPORARY;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_CONVERSATION_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import javax.jms.ConnectionFactory;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
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
  private static final CountDownLatch faultProcessed = new CountDownLatch(1);

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
            from("direct:faultInput")
                .to("jms:queue:faultQueue?exchangePattern=InOut&transferFault=true");
            from("jms:queue:faultQueue?transferFault=true")
                .process(
                    exchange -> {
                      exchange.getOut().setBody("fault reply");
                      exchange.getOut().setFault(true);
                      faultProcessed.countDown();
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

    testing.waitAndAssertSortedTraces(
        Comparator.comparingInt(trace -> trace.size()),
        JmsCamelTest::assertJmsReceiveTrace,
        JmsCamelTest::assertCamelTrace);

    assertSendAndProcessMetrics(testing, "jms", "testQueue");
  }

  @Test
  void failedCamelProcessCountsDeliveredMessage() throws Exception {
    ProducerTemplate template = camelContext.createProducerTemplate();
    template.sendBody("direct:errorInput", "test message");

    assertThat(errorProcessed.await(1, MINUTES)).isTrue();
    testing.waitForTraces(2);
    assertSendAndProcessMetrics(
        testing, "jms", "errorQueue", IllegalStateException.class.getName());
  }

  @Test
  void faultedCamelProcessCountsDeliveredMessage() throws Exception {
    ProducerTemplate template = camelContext.createProducerTemplate();
    Exchange result =
        template.request("direct:faultInput", exchange -> exchange.getIn().setBody("test message"));

    assertThat(faultProcessed.await(1, MINUTES)).isTrue();
    assertThat(result.getOut().isFault()).isTrue();
    assertThat(result.getOut().getBody()).isEqualTo("fault reply");
    assertThat(result.getException()).isNull();
    testing.waitAndAssertSortedTraces(
        Comparator.comparingInt((List<SpanData> trace) -> trace.size())
            .thenComparing(trace -> trace.get(0).getName()),
        JmsCamelTest::assertReplyReceiveTrace,
        JmsCamelTest::assertFaultJmsReceiveTrace,
        JmsCamelTest::assertReplySendTrace,
        JmsCamelTest::assertFaultCamelTrace);
    assertSendAndProcessMetrics(testing, "jms", "faultQueue", "_OTHER", "_OTHER", null);
  }

  private static void assertReplyReceiveTrace(TraceAssert trace) {
    trace.hasSpansSatisfyingExactly(
        span ->
            span.hasName("receive")
                .hasKind(SpanKind.CLIENT)
                .hasNoParent()
                .hasAttributesSatisfyingExactly(
                    equalTo(stringKey("messaging.system.name"), "jms"),
                    satisfies(MESSAGING_DESTINATION_NAME, val -> val.isNotEmpty()),
                    equalTo(MESSAGING_DESTINATION_TEMPORARY, true),
                    equalTo(MESSAGING_OPERATION_NAME, "receive"),
                    equalTo(MESSAGING_OPERATION_TYPE, "receive"),
                    satisfies(MESSAGING_MESSAGE_ID, val -> val.isInstanceOf(String.class)),
                    satisfies(
                        MESSAGING_MESSAGE_CONVERSATION_ID, val -> val.isInstanceOf(String.class))));
  }

  private static void assertFaultJmsReceiveTrace(TraceAssert trace) {
    trace.hasSpansSatisfyingExactly(
        span ->
            span.hasName("receive faultQueue")
                .hasKind(SpanKind.CLIENT)
                .hasNoParent()
                .hasTotalRecordedLinks(1)
                .hasAttributesSatisfyingExactly(
                    equalTo(stringKey("messaging.system.name"), "jms"),
                    equalTo(MESSAGING_DESTINATION_NAME, "faultQueue"),
                    equalTo(MESSAGING_OPERATION_NAME, "receive"),
                    equalTo(MESSAGING_OPERATION_TYPE, "receive"),
                    satisfies(MESSAGING_MESSAGE_ID, val -> val.isInstanceOf(String.class)),
                    satisfies(
                        MESSAGING_MESSAGE_CONVERSATION_ID, val -> val.isInstanceOf(String.class))));
  }

  private static void assertReplySendTrace(TraceAssert trace) {
    trace.hasSpansSatisfyingExactly(
        span ->
            span.hasName("send")
                .hasKind(SpanKind.PRODUCER)
                .hasNoParent()
                .hasAttributesSatisfyingExactly(
                    equalTo(stringKey("messaging.system.name"), "jms"),
                    satisfies(MESSAGING_DESTINATION_NAME, val -> val.isNotEmpty()),
                    equalTo(MESSAGING_DESTINATION_TEMPORARY, true),
                    equalTo(MESSAGING_OPERATION_NAME, "send"),
                    equalTo(MESSAGING_OPERATION_TYPE, "send"),
                    satisfies(MESSAGING_MESSAGE_ID, val -> val.isInstanceOf(String.class)),
                    satisfies(
                        MESSAGING_MESSAGE_CONVERSATION_ID, val -> val.isInstanceOf(String.class))));
  }

  private static void assertFaultCamelTrace(TraceAssert trace) {
    trace.hasSpansSatisfyingExactly(
        span -> span.hasName("faultInput").hasKind(SpanKind.INTERNAL).hasNoParent(),
        span ->
            span.hasName("send faultQueue")
                .hasKind(SpanKind.PRODUCER)
                .hasParent(trace.getSpan(0))
                .hasStatus(StatusData.error())
                .hasAttributesSatisfyingExactly(
                    equalTo(stringKey("messaging.system.name"), "jms"),
                    equalTo(MESSAGING_DESTINATION_NAME, "faultQueue"),
                    equalTo(MESSAGING_OPERATION_NAME, "send"),
                    equalTo(MESSAGING_OPERATION_TYPE, "send"),
                    equalTo(
                        stringKey("camel.uri"),
                        experimental(
                            "jms://queue:faultQueue?exchangePattern=InOut&transferFault=true")),
                    equalTo(ERROR_TYPE, "_OTHER")),
        span ->
            span.hasName("process faultQueue")
                .hasKind(SpanKind.CONSUMER)
                .hasParent(trace.getSpan(1))
                .hasStatus(StatusData.error())
                .hasAttributesSatisfyingExactly(
                    equalTo(stringKey("messaging.system.name"), "jms"),
                    equalTo(MESSAGING_DESTINATION_NAME, "faultQueue"),
                    equalTo(MESSAGING_OPERATION_NAME, "process"),
                    equalTo(MESSAGING_OPERATION_TYPE, "process"),
                    satisfies(MESSAGING_MESSAGE_ID, val -> val.isInstanceOf(String.class)),
                    equalTo(
                        stringKey("camel.uri"),
                        experimental("jms://queue:faultQueue?transferFault=true")),
                    equalTo(ERROR_TYPE, "_OTHER")));
  }

  private static void assertJmsReceiveTrace(TraceAssert trace) {
    trace.hasSpansSatisfyingExactly(
        span ->
            span.hasName("receive testQueue")
                .hasKind(SpanKind.CLIENT)
                .hasNoParent()
                .hasTotalRecordedLinks(1)
                .hasAttributesSatisfyingExactly(
                    equalTo(stringKey("messaging.system.name"), "jms"),
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
            span.hasName("send testQueue")
                .hasKind(SpanKind.PRODUCER)
                .hasParent(trace.getSpan(0))
                .hasAttributesSatisfyingExactly(
                    equalTo(stringKey("messaging.system.name"), "jms"),
                    equalTo(MESSAGING_DESTINATION_NAME, "testQueue"),
                    equalTo(MESSAGING_OPERATION_NAME, "send"),
                    equalTo(MESSAGING_OPERATION_TYPE, "send"),
                    equalTo(stringKey("camel.uri"), experimental("jms://queue:testQueue"))));

    int processSpanIndex = assertions.size();
    assertions.add(
        span -> {
          span.hasName("process testQueue")
              .hasKind(SpanKind.CONSUMER)
              .hasParent(trace.getSpan(1))
              .hasAttributesSatisfyingExactly(
                  equalTo(stringKey("messaging.system.name"), "jms"),
                  equalTo(MESSAGING_DESTINATION_NAME, "testQueue"),
                  equalTo(MESSAGING_OPERATION_NAME, "process"),
                  equalTo(MESSAGING_OPERATION_TYPE, "process"),
                  equalTo(stringKey("camel.uri"), experimental("jms://queue:testQueue")),
                  satisfies(MESSAGING_MESSAGE_ID, val -> val.isInstanceOf(String.class)));
          span.hasLinks(LinkData.create(asRemote(trace.getSpan(1).getSpanContext())));
        });
    assertions.add(
        span ->
            span.hasName("mock")
                .hasKind(SpanKind.CLIENT)
                .hasParent(trace.getSpan(processSpanIndex)));
    trace.hasSpansSatisfyingExactly(assertions);
  }
}
