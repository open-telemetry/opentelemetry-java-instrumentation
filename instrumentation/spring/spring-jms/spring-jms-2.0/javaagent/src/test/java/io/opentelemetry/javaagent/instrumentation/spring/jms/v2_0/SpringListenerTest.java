/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v2_0;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.instrumentation.spring.jms.v2_0.AbstractJmsTest;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.concurrent.atomic.AtomicReference;
import javax.jms.ConnectionFactory;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jms.config.JmsListenerEndpointRegistry;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.DefaultMessageListenerContainer;

class SpringListenerTest extends AbstractJmsTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @ParameterizedTest
  @ValueSource(classes = {AnnotatedListenerConfig.class, ManualListenerConfig.class})
  void receivingMessageInSpringListenerGeneratesSpans(Class<? extends AbstractConfig> config) {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(config);
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

    AtomicReference<SpanData> producerSpan = new AtomicReference<>();
    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(PRODUCER, emitStableMessagingSemconv() ? CLIENT : CONSUMER),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> assertProducerSpan(span, "SpringListenerJms2", false));
          producerSpan.set(trace.getSpan(0));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    assertConsumerSpan(
                        span,
                        producerSpan.get(),
                        null,
                        "SpringListenerJms2",
                        "receive",
                        false,
                        null,
                        "durable-subscription"),
                span ->
                    assertConsumerSpan(
                        span,
                        producerSpan.get(),
                        trace.getSpan(0),
                        "SpringListenerJms2",
                        "process",
                        false,
                        null,
                        "durable-subscription")));
  }

  @ParameterizedTest
  @ValueSource(classes = {AnnotatedListenerConfig.class, ManualListenerConfig.class})
  @EnabledIfSystemProperty(named = "testJmsDisabled", matches = "true")
  void receivingMessageInSpringListenerGeneratesSpansWithJmsDisabled(
      Class<? extends AbstractConfig> config) {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(config);
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
                span ->
                    assertConsumerSpan(
                        span,
                        null,
                        null,
                        "SpringListenerJms2",
                        "process",
                        false,
                        null,
                        "durable-subscription")));
  }
}
