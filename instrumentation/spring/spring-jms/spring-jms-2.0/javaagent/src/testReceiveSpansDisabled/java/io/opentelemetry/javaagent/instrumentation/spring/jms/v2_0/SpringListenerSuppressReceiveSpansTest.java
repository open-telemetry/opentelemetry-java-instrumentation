/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v2_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.testing.junit.MessagingMetricsAssertions.assertCounter;
import static io.opentelemetry.instrumentation.testing.junit.MessagingMetricsAssertions.assertHistogram;
import static io.opentelemetry.instrumentation.testing.junit.MessagingMetricsAssertions.assertNoMetric;
import static io.opentelemetry.instrumentation.testing.junit.MessagingMetricsAssertions.assertNoStableMetrics;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_SUBSCRIPTION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.spring.jms.v2_0.AbstractJmsTest;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import javax.jms.ConnectionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jms.config.JmsListenerEndpointRegistry;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.DefaultMessageListenerContainer;

class SpringListenerSuppressReceiveSpansTest extends AbstractJmsTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Test
  void receivingMessageInSpringListenerGeneratesSpans() {
    AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(AnnotatedListenerConfig.class);
    cleanup.deferCleanup(context);
    JmsListenerEndpointRegistry registry = context.getBean(JmsListenerEndpointRegistry.class);
    await()
        .until(
            () ->
                registry.getListenerContainers().stream()
                    .map(DefaultMessageListenerContainer.class::cast)
                    .allMatch(DefaultMessageListenerContainer::isRegisteredWithDestination));
    ConnectionFactory factory = context.getBean(ConnectionFactory.class);
    JmsTemplate template = new JmsTemplate(factory);
    template.setPubSubDomain(true);

    template.convertAndSend("SpringListenerJms2", "a message");
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertProducerSpan(span, "SpringListenerJms2", false),
                span ->
                    assertConsumerSpan(
                        span,
                        emitStableMessagingSemconv() ? trace.getSpan(0) : null,
                        trace.getSpan(0),
                        "SpringListenerJms2",
                        "process",
                        false,
                        null,
                        "durable-subscription")));

    if (!emitStableMessagingSemconv()) {
      assertNoStableMetrics(testing, "io.opentelemetry.jms-1.1");
      assertNoStableMetrics(testing, "io.opentelemetry.spring-jms-2.0");
      return;
    }

    Attributes sendAttributes =
        Attributes.of(
            MESSAGING_OPERATION_NAME,
            "send",
            MESSAGING_SYSTEM,
            "jms",
            MESSAGING_DESTINATION_NAME,
            "SpringListenerJms2");
    assertHistogram(
        testing,
        "io.opentelemetry.jms-1.1",
        "messaging.client.operation.duration",
        sendAttributes.toBuilder().put(MESSAGING_OPERATION_TYPE, "send").build());
    assertNoMetric(testing, "io.opentelemetry.jms-1.1", "messaging.client.consumed.messages");

    Attributes processAttributes =
        Attributes.builder()
            .put(MESSAGING_OPERATION_NAME, "process")
            .put(MESSAGING_SYSTEM, "jms")
            .put(MESSAGING_DESTINATION_NAME, "SpringListenerJms2")
            .put(MESSAGING_DESTINATION_SUBSCRIPTION_NAME, "durable-subscription")
            .build();
    assertHistogram(
        testing,
        "io.opentelemetry.spring-jms-2.0",
        "messaging.process.duration",
        processAttributes);
    assertCounter(
        testing,
        "io.opentelemetry.spring-jms-2.0",
        "messaging.client.consumed.messages",
        1,
        processAttributes);
  }
}
