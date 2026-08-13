/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v6_0;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.testing.junit.MessagingMetricsAssertions.assertCounter;
import static io.opentelemetry.instrumentation.testing.junit.MessagingMetricsAssertions.assertNoMetric;
import static io.opentelemetry.instrumentation.testing.junit.MessagingMetricsAssertions.assertNoStableMetrics;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanName;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_SUBSCRIPTION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.AbstractStringAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.annotation.JmsListenerConfigurer;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerEndpoint;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.listener.MessageListenerContainer;
import org.springframework.jms.listener.SessionAwareMessageListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@SuppressWarnings("deprecation") // using deprecated semconv
class SpringJmsListenerTest extends AbstractSpringJmsListenerTest {

  @Test
  @SuppressWarnings("unchecked")
  void capturesDefaultSubscriptionName() throws Exception {
    SpringApplication app = new SpringApplication(DefaultSubscriptionNameConfig.class);
    app.setDefaultProperties(defaultConfig());
    ConfigurableApplicationContext applicationContext = app.run();
    cleanup.deferCleanup(applicationContext);
    awaitDurableSubscriptions(applicationContext);

    JmsTemplate jmsTemplate = new JmsTemplate(applicationContext.getBean(ConnectionFactory.class));
    jmsTemplate.setPubSubDomain(true);
    String message = "hello there";

    testing.runWithSpan(
        "parent",
        () -> jmsTemplate.convertAndSend(DefaultSubscriptionNameConfig.DESTINATION_NAME, message));

    CompletableFuture<String> receivedMessage =
        applicationContext.getBean("receivedMessage", CompletableFuture.class);
    assertThat(receivedMessage.get(10, SECONDS)).isEqualTo(message);

    assertSpringJmsListener(
        DefaultSubscriptionNameConfig.DESTINATION_NAME,
        DefaultSubscriptionNameListener.class.getName());
  }

  @Test
  @SuppressWarnings("unchecked")
  void capturesSharedSubscriptionName() throws Exception {
    SpringApplication app = new SpringApplication(SharedSubscriptionConfig.class);
    app.setDefaultProperties(defaultConfig());
    ConfigurableApplicationContext applicationContext = app.run();
    cleanup.deferCleanup(applicationContext);
    awaitDurableSubscriptions(applicationContext);

    JmsTemplate jmsTemplate = new JmsTemplate(applicationContext.getBean(ConnectionFactory.class));
    jmsTemplate.setPubSubDomain(true);
    String message = "hello there";

    testing.runWithSpan(
        "parent",
        () -> jmsTemplate.convertAndSend(SharedSubscriptionConfig.DESTINATION_NAME, message));

    CompletableFuture<String> receivedMessage =
        applicationContext.getBean("receivedMessage", CompletableFuture.class);
    assertThat(receivedMessage.get(10, SECONDS)).isEqualTo(message);

    assertSpringJmsListener(
        SharedSubscriptionConfig.DESTINATION_NAME, SharedSubscriptionConfig.SUBSCRIPTION_NAME);
  }

  @Test
  @SuppressWarnings("unchecked")
  void processSpanUsesSemconvParentWithReceiveSpan() throws Exception {
    Tracer tracer = testing.getOpenTelemetry().getTracer("test");
    Span ambient = tracer.spanBuilder("ambient").startSpan();

    SpringApplication app = new SpringApplication(AmbientParentConfig.class);
    app.setDefaultProperties(defaultConfig());
    app.setAdditionalProfiles("ambient-parent");
    ConfigurableApplicationContext applicationContext;
    try (Scope ignored = Context.root().with(ambient).makeCurrent()) {
      applicationContext = app.run();
    }

    try {
      JmsTemplate jmsTemplate =
          new JmsTemplate(applicationContext.getBean(ConnectionFactory.class));
      testing.runWithSpan(
          "producer parent",
          () -> jmsTemplate.convertAndSend("spring-jms-listener", "hello there"));

      CompletableFuture<String> receivedMessage =
          applicationContext.getBean(CompletableFuture.class);
      assertThat(receivedMessage.get(10, SECONDS)).isEqualTo("hello there");
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
                              ? "send spring-jms-listener"
                              : "spring-jms-listener publish")
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
                                ? "receive spring-jms-listener"
                                : "spring-jms-listener receive")
                        .hasKind(emitStableMessagingSemconv() ? CLIENT : CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext())),
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "process spring-jms-listener"
                                : "spring-jms-listener process")
                        .hasKind(CONSUMER)
                        .hasParent(trace.getSpan(emitStableMessagingSemconv() ? 0 : 1))
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))));
  }

  @Override
  void assertSpringJmsListener() {
    assertSpringJmsListener("spring-jms-listener", "durable-subscription");
  }

  private static void assertSpringJmsListener(String destinationName, String subscriptionName) {
    if (emitStableMessagingSemconv()) {
      AtomicReference<SpanData> producerSpan = new AtomicReference<>();
      testing.waitAndAssertSortedTraces(
          orderByRootSpanKind(INTERNAL, CLIENT),
          trace -> {
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName("send " + destinationName)
                        .hasKind(PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            equalTo(MESSAGING_DESTINATION_NAME, destinationName),
                            oldOperation("publish"),
                            operationName("send"),
                            operationType("send"),
                            satisfies(MESSAGING_MESSAGE_ID, AbstractStringAssert::isNotBlank)),
                span ->
                    span.hasName("process " + destinationName)
                        .hasKind(CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasLinks(LinkData.create(trace.getSpan(1).getSpanContext()))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            equalTo(MESSAGING_DESTINATION_NAME, destinationName),
                            oldOperation("process"),
                            operationName("process"),
                            operationType("process"),
                            satisfies(MESSAGING_MESSAGE_ID, AbstractStringAssert::isNotBlank),
                            subscriptionName(subscriptionName)),
                span -> span.hasName("consumer").hasParent(trace.getSpan(2)));
            producerSpan.set(trace.getSpan(1));
          },
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("receive " + destinationName)
                          .hasKind(CLIENT)
                          .hasNoParent()
                          .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                          .hasAttributesSatisfyingExactly(
                              equalTo(MESSAGING_SYSTEM, "jms"),
                              equalTo(MESSAGING_DESTINATION_NAME, destinationName),
                              oldOperation("receive"),
                              operationName("receive"),
                              operationType("receive"),
                              satisfies(MESSAGING_MESSAGE_ID, AbstractStringAssert::isNotBlank),
                              subscriptionName(subscriptionName))));
      return;
    }

    AtomicReference<SpanData> producerSpan = new AtomicReference<>();
    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(INTERNAL, CONSUMER),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasNoParent(),
              span ->
                  span.hasName(destinationName + " publish")
                      .hasKind(PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          equalTo(MESSAGING_SYSTEM, "jms"),
                          equalTo(MESSAGING_DESTINATION_NAME, destinationName),
                          oldOperation("publish"),
                          operationName("send"),
                          operationType("send"),
                          satisfies(MESSAGING_MESSAGE_ID, AbstractStringAssert::isNotBlank)));

          producerSpan.set(trace.getSpan(1));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(destinationName + " receive")
                        .hasKind(CONSUMER)
                        .hasNoParent()
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            equalTo(MESSAGING_DESTINATION_NAME, destinationName),
                            oldOperation("receive"),
                            operationName("receive"),
                            operationType("receive"),
                            satisfies(MESSAGING_MESSAGE_ID, AbstractStringAssert::isNotBlank),
                            subscriptionName(subscriptionName)),
                span ->
                    span.hasName(destinationName + " process")
                        .hasKind(CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            equalTo(MESSAGING_DESTINATION_NAME, destinationName),
                            oldOperation("process"),
                            operationName("process"),
                            operationType("process"),
                            satisfies(MESSAGING_MESSAGE_ID, AbstractStringAssert::isNotBlank),
                            subscriptionName(subscriptionName)),
                span -> span.hasName("consumer").hasParent(trace.getSpan(1))));

    if (!emitStableMessagingSemconv()) {
      assertNoStableMetrics(testing, "io.opentelemetry.jms-3.0");
      assertNoStableMetrics(testing, "io.opentelemetry.spring-jms-6.0");
      return;
    }

    Attributes receiveAttributes =
        Attributes.builder()
            .put(MESSAGING_OPERATION_NAME, "receive")
            .put(MESSAGING_SYSTEM, "jms")
            .put(MESSAGING_DESTINATION_NAME, "spring-jms-listener")
            .put(MESSAGING_DESTINATION_SUBSCRIPTION_NAME, "durable-subscription")
            .build();
    assertCounter(
        testing,
        "io.opentelemetry.jms-3.0",
        "messaging.client.consumed.messages",
        1,
        receiveAttributes);
    assertNoMetric(
        testing, "io.opentelemetry.spring-jms-6.0", "messaging.client.consumed.messages");
  }

  @ParameterizedTest
  @ValueSource(classes = {AnnotatedListenerConfig.class, ManualListenerConfig.class})
  @SuppressWarnings("unchecked")
  void shouldCaptureHeaders(Class<?> configClass) throws Exception {
    // given
    SpringApplication app = new SpringApplication(configClass);
    app.setDefaultProperties(defaultConfig());
    ConfigurableApplicationContext applicationContext = app.run();
    cleanup.deferCleanup(applicationContext);
    awaitDurableSubscriptions(applicationContext);

    JmsTemplate jmsTemplate = new JmsTemplate(applicationContext.getBean(ConnectionFactory.class));
    jmsTemplate.setPubSubDomain(true);
    String message = "hello there";

    // when
    testing.runWithSpan(
        "parent",
        () ->
            jmsTemplate.convertAndSend(
                "spring-jms-listener",
                message,
                jmsMessage -> {
                  jmsMessage.setStringProperty("Test_Message_Header", "test");
                  jmsMessage.setStringProperty("Uncaptured_Header", "password");
                  jmsMessage.setIntProperty("Test_Message_Int_Header", 1234);
                  return jmsMessage;
                }));

    // then
    CompletableFuture<String> receivedMessage =
        applicationContext.getBean("receivedMessage", CompletableFuture.class);
    assertThat(receivedMessage.get(10, SECONDS)).isEqualTo(message);

    if (emitStableMessagingSemconv()) {
      AtomicReference<SpanData> producerSpan = new AtomicReference<>();
      testing.waitAndAssertSortedTraces(
          orderByRootSpanKind(INTERNAL, CLIENT),
          trace -> {
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName("send spring-jms-listener")
                        .hasKind(PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            equalTo(MESSAGING_DESTINATION_NAME, "spring-jms-listener"),
                            oldOperation("publish"),
                            operationName("send"),
                            operationType("send"),
                            satisfies(MESSAGING_MESSAGE_ID, AbstractStringAssert::isNotBlank),
                            equalTo(
                                stringArrayKey("messaging.header.Test_Message_Header"),
                                singletonList("test")),
                            equalTo(
                                stringArrayKey("messaging.header.Test_Message_Int_Header"),
                                singletonList("1234"))),
                span ->
                    span.hasName("process spring-jms-listener")
                        .hasKind(CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasLinks(LinkData.create(trace.getSpan(1).getSpanContext()))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            equalTo(MESSAGING_DESTINATION_NAME, "spring-jms-listener"),
                            oldOperation("process"),
                            operationName("process"),
                            operationType("process"),
                            satisfies(MESSAGING_MESSAGE_ID, AbstractStringAssert::isNotBlank),
                            subscriptionName("durable-subscription"),
                            equalTo(
                                stringArrayKey("messaging.header.Test_Message_Header"),
                                singletonList("test")),
                            equalTo(
                                stringArrayKey("messaging.header.Test_Message_Int_Header"),
                                singletonList("1234"))),
                span -> span.hasName("consumer").hasParent(trace.getSpan(2)));
            producerSpan.set(trace.getSpan(1));
          },
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("receive spring-jms-listener")
                          .hasKind(CLIENT)
                          .hasNoParent()
                          .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                          .hasAttributesSatisfyingExactly(
                              equalTo(MESSAGING_SYSTEM, "jms"),
                              equalTo(MESSAGING_DESTINATION_NAME, "spring-jms-listener"),
                              oldOperation("receive"),
                              operationName("receive"),
                              operationType("receive"),
                              satisfies(MESSAGING_MESSAGE_ID, AbstractStringAssert::isNotBlank),
                              subscriptionName("durable-subscription"),
                              equalTo(
                                  stringArrayKey("messaging.header.Test_Message_Header"),
                                  singletonList("test")),
                              equalTo(
                                  stringArrayKey("messaging.header.Test_Message_Int_Header"),
                                  singletonList("1234")))));
      return;
    }

    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(INTERNAL, CONSUMER),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName("spring-jms-listener publish")
                        .hasKind(PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            equalTo(MESSAGING_DESTINATION_NAME, "spring-jms-listener"),
                            oldOperation("publish"),
                            operationName("send"),
                            operationType("send"),
                            satisfies(MESSAGING_MESSAGE_ID, AbstractStringAssert::isNotBlank),
                            equalTo(
                                stringArrayKey("messaging.header.Test_Message_Header"),
                                singletonList("test")),
                            equalTo(
                                stringArrayKey("messaging.header.Test_Message_Int_Header"),
                                singletonList("1234")))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("spring-jms-listener receive")
                        .hasKind(CONSUMER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            equalTo(MESSAGING_DESTINATION_NAME, "spring-jms-listener"),
                            oldOperation("receive"),
                            operationName("receive"),
                            operationType("receive"),
                            satisfies(MESSAGING_MESSAGE_ID, AbstractStringAssert::isNotBlank),
                            subscriptionName("durable-subscription"),
                            equalTo(
                                stringArrayKey("messaging.header.Test_Message_Header"),
                                singletonList("test")),
                            equalTo(
                                stringArrayKey("messaging.header.Test_Message_Int_Header"),
                                singletonList("1234"))),
                span ->
                    span.hasName("spring-jms-listener process")
                        .hasKind(CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            equalTo(MESSAGING_DESTINATION_NAME, "spring-jms-listener"),
                            oldOperation("process"),
                            operationName("process"),
                            operationType("process"),
                            satisfies(MESSAGING_MESSAGE_ID, AbstractStringAssert::isNotBlank),
                            subscriptionName("durable-subscription"),
                            equalTo(
                                stringArrayKey("messaging.header.Test_Message_Header"),
                                singletonList("test")),
                            equalTo(
                                stringArrayKey("messaging.header.Test_Message_Int_Header"),
                                singletonList("1234"))),
                span -> span.hasName("consumer").hasParent(trace.getSpan(1))));
  }

  @ParameterizedTest
  @ValueSource(classes = {AnnotatedListenerConfig.class, ManualListenerConfig.class})
  @EnabledIfSystemProperty(named = "testJmsDisabled", matches = "true")
  @SuppressWarnings("unchecked")
  void testSpringJmsListenerWithJmsDisabled(Class<?> configClass) throws Exception {
    SpringApplication app = new SpringApplication(configClass);
    app.setDefaultProperties(defaultConfig());
    ConfigurableApplicationContext applicationContext = app.run();
    cleanup.deferCleanup(applicationContext);
    awaitDurableSubscriptions(applicationContext);

    JmsTemplate jmsTemplate = new JmsTemplate(applicationContext.getBean(ConnectionFactory.class));
    jmsTemplate.setPubSubDomain(true);
    String message = "hello there";

    testing.runWithSpan("parent", () -> jmsTemplate.convertAndSend("spring-jms-listener", message));

    CompletableFuture<String> receivedMessage =
        applicationContext.getBean("receivedMessage", CompletableFuture.class);
    assertThat(receivedMessage.get(10, SECONDS)).isEqualTo(message);

    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(INTERNAL, CONSUMER),
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("parent").hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertProcessSpanWithSubscription(span),
                span -> span.hasName("consumer").hasParent(trace.getSpan(0))));
  }

  private static void assertProcessSpanWithSubscription(SpanDataAssert span) {
    span.hasName("process spring-jms-listener")
        .hasKind(CONSUMER)
        .hasNoParent()
        .hasAttributesSatisfyingExactly(
            equalTo(MESSAGING_SYSTEM, "jms"),
            equalTo(MESSAGING_DESTINATION_NAME, "spring-jms-listener"),
            oldOperation("process"),
            operationName("process"),
            operationType("process"),
            satisfies(MESSAGING_MESSAGE_ID, AbstractStringAssert::isNotBlank),
            subscriptionName("durable-subscription"));
  }

  private static AttributeAssertion oldOperation(String operation) {
    return equalTo(MESSAGING_OPERATION, emitOldMessagingSemconv() ? operation : null);
  }

  private static AttributeAssertion operationName(String operation) {
    return equalTo(MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? operation : null);
  }

  private static AttributeAssertion operationType(String operation) {
    return equalTo(MESSAGING_OPERATION_TYPE, emitStableMessagingSemconv() ? operation : null);
  }

  private static AttributeAssertion subscriptionName(String subscriptionName) {
    return equalTo(
        MESSAGING_DESTINATION_SUBSCRIPTION_NAME,
        emitStableMessagingSemconv() ? subscriptionName : null);
  }

  @TestConfiguration
  @EnableJms
  @Profile("ambient-parent")
  static class AmbientParentConfig extends AbstractConfig {

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
                  container.setDestinationName("spring-jms-listener");
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
