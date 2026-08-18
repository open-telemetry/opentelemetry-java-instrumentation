/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v2_0;

import static org.awaitility.Awaitility.await;

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
                        null,
                        trace.getSpan(0),
                        "SpringListenerJms2",
                        "process",
                        false,
                        null,
                        "durable-subscription")));
  }
}
