/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v2_0;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.testing.junit.MessagingMetricsAssertions.assertCounter;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanName;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_SUBSCRIPTION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.spring.jms.v2_0.AbstractJmsTest;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.TextMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.annotation.JmsListenerConfigurer;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerEndpoint;
import org.springframework.jms.config.JmsListenerEndpointRegistry;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.jms.listener.MessageListenerContainer;
import org.springframework.jms.listener.SessionAwareMessageListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class SpringListenerTest extends AbstractJmsTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Test
  void capturesDefaultSubscriptionName() {
    runListenerTest(
        DefaultSubscriptionNameConfig.class, DefaultSubscriptionNameListener.class.getName());
  }

  @Test
  void capturesLegacyDurableSubscriptionName() {
    runListenerTest(LegacyDurableSubscriptionConfig.class, "legacy-durable-subscription");
  }

  @Test
  @SuppressWarnings("unchecked")
  void processSpanUsesSemconvParentWithReceiveSpan() throws Exception {
    Tracer tracer = testing.getOpenTelemetry().getTracer("test");
    Span ambient = tracer.spanBuilder("ambient").startSpan();

    AnnotationConfigApplicationContext applicationContext;
    try (Scope ignored = Context.root().with(ambient).makeCurrent()) {
      applicationContext = new AnnotationConfigApplicationContext();
      applicationContext.getEnvironment().setActiveProfiles("ambient-parent");
      applicationContext.register(AmbientParentConfig.class);
      applicationContext.refresh();
    }

    try {
      ConnectionFactory factory = applicationContext.getBean(ConnectionFactory.class);
      JmsTemplate template = new JmsTemplate(factory);
      testing.runWithSpan(
          "producer parent", () -> template.convertAndSend("SpringListenerJms2", "a message"));

      CompletableFuture<String> receivedMessage =
          applicationContext.getBean(CompletableFuture.class);
      assertThat(receivedMessage.get(10, SECONDS)).isEqualTo("a message");
    } finally {
      applicationContext.close();
      ambient.end();
    }

    AtomicReference<SpanData> producerSpan = new AtomicReference<>();
    testing.waitAndAssertSortedTraces(
        orderByRootSpanName("producer parent", "ambient"),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("producer parent").hasNoParent(),
              span ->
                  span.hasName(
                          emitStableMessagingSemconv()
                              ? "send SpringListenerJms2"
                              : "SpringListenerJms2 publish")
                      .hasKind(PRODUCER)
                      .hasParent(trace.getSpan(0)));
          producerSpan.set(trace.getSpan(1));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("ambient").hasNoParent(),
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "receive SpringListenerJms2"
                                : "SpringListenerJms2 receive")
                        .hasKind(emitStableMessagingSemconv() ? CLIENT : CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext())),
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "process SpringListenerJms2"
                                : "SpringListenerJms2 process")
                        .hasKind(CONSUMER)
                        .hasParent(trace.getSpan(emitStableMessagingSemconv() ? 0 : 1))
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))));
  }

  @ParameterizedTest
  @ValueSource(classes = {AnnotatedListenerConfig.class, ManualListenerConfig.class})
  void receivingMessageInSpringListenerGeneratesSpans(Class<? extends AbstractConfig> config) {
    runListenerTest(config, "durable-subscription");
  }

  private void runListenerTest(Class<? extends AbstractConfig> config, String subscriptionName) {
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
    if (emitStableMessagingSemconv()) {
      testing.waitAndAssertSortedTraces(
          orderByRootSpanKind(PRODUCER, CLIENT),
          trace -> {
            trace.hasSpansSatisfyingExactly(
                span -> assertProducerSpan(span, "SpringListenerJms2", false),
                span ->
                    assertConsumerSpan(
                        span,
                        trace.getSpan(0),
                        trace.getSpan(0),
                        "SpringListenerJms2",
                        "process",
                        false,
                        null,
                        subscriptionName));
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
                          subscriptionName)));
      return;
    }

    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(PRODUCER, CONSUMER),
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
                        subscriptionName),
                span ->
                    assertConsumerSpan(
                        span,
                        producerSpan.get(),
                        trace.getSpan(0),
                        "SpringListenerJms2",
                        "process",
                        false,
                        null,
                        subscriptionName)));
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

    // the jms instrumentation that would create the receive operation is disabled, so the process
    // operation counts the consumed message
    Attributes processAttributes =
        Attributes.builder()
            .put(MESSAGING_OPERATION_NAME, "process")
            .put(MESSAGING_SYSTEM, "jms")
            .put(MESSAGING_DESTINATION_NAME, "SpringListenerJms2")
            .put(MESSAGING_DESTINATION_SUBSCRIPTION_NAME, "durable-subscription")
            .build();
    assertCounter(
        testing,
        "io.opentelemetry.spring-jms-2.0",
        "messaging.client.consumed.messages",
        1,
        processAttributes);
  }

  @TestConfiguration
  @EnableJms
  @Profile("ambient-parent")
  static class AmbientParentConfig extends AbstractConfig {

    @Bean
    CompletableFuture<String> receivedMessage() {
      return new CompletableFuture<>();
    }

    @Bean
    JmsListenerConfigurer ambientParentListenerConfigurer(
        CompletableFuture<String> receivedMessage) {
      return registrar ->
          registrar.registerEndpoint(
              new JmsListenerEndpoint() {
                @Override
                public String getId() {
                  return "ambient-parent-listener";
                }

                @Override
                public void setupListenerContainer(MessageListenerContainer listenerContainer) {
                  AbstractMessageListenerContainer container =
                      (AbstractMessageListenerContainer) listenerContainer;
                  container.setDestinationName("SpringListenerJms2");
                  container.setupMessageListener(
                      (SessionAwareMessageListener<Message>)
                          (message, session) ->
                              receivedMessage.complete(((TextMessage) message).getText()));
                }
              });
    }

    @Bean
    ThreadPoolTaskExecutor ambientParentTaskExecutor() {
      Context ambientContext = Context.current();
      ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
      executor.setCorePoolSize(1);
      executor.setMaxPoolSize(1);
      executor.setTaskDecorator(ambientContext::wrap);
      return executor;
    }

    @Override
    @Bean
    JmsListenerContainerFactory<?> jmsListenerContainerFactory(
        ConnectionFactory connectionFactory) {
      DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
      factory.setConnectionFactory(connectionFactory);
      factory.setTaskExecutor(ambientParentTaskExecutor());
      return factory;
    }
  }
}
