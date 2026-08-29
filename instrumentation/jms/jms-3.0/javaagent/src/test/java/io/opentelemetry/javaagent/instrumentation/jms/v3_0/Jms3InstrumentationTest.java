/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v3_0;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageListener;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import jakarta.jms.Topic;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.activemq.artemis.jms.client.ActiveMQDestination;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class Jms3InstrumentationTest extends AbstractJms3Test {

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  void capturesDurableConsumerName() throws Exception {
    Topic topic = session.createTopic("durable-topic");
    TextMessage sentMessage = session.createTextMessage("hello there");
    MessageProducer producer = session.createProducer(topic);
    cleanup.deferCleanup(producer);
    MessageConsumer consumer = session.createDurableConsumer(topic, "durable-subscription");
    cleanup.deferCleanup(consumer);

    testing.runWithSpan("producer parent", () -> producer.send(sentMessage));
    CompletableFuture<TextMessage> receivedMessage = new CompletableFuture<>();
    MessageListener listener = message -> receivedMessage.complete((TextMessage) message);
    consumer.setMessageListener(listener);
    assertThat(consumer.getMessageListener()).isSameAs(listener);

    String messageId = receivedMessage.get(10, SECONDS).getJMSMessageID();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("producer parent").hasNoParent(),
                span ->
                    span.hasKind(PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            messagingDestinationName("durable-topic", "durable-topic"),
                            oldOperation("publish"),
                            operationName("send"),
                            operationType("send"),
                            equalTo(MESSAGING_MESSAGE_ID, messageId),
                            messagingTempDestination(false)),
                span ->
                    span.hasKind(CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            messagingDestinationName("durable-topic", "durable-topic"),
                            oldOperation("process"),
                            operationName("process"),
                            operationType("process"),
                            equalTo(MESSAGING_MESSAGE_ID, messageId),
                            messagingTempDestination(false),
                            subscriptionName("durable-subscription"))));
  }

  @Test
  void overwritesSubscriptionNameWhenListenerIsReregistered() throws JMSException {
    String topicName = "reregistered-listener-topic";
    Topic topic = session.createTopic(topicName);
    TextMessage message = session.createTextMessage("hello there");
    message.setJMSDestination(topic);
    MessageListener listener = ignored -> {};

    MessageConsumer durableConsumer =
        session.createDurableConsumer(topic, "reregistered-subscription");
    cleanup.deferCleanup(durableConsumer);
    durableConsumer.setMessageListener(listener);

    MessageConsumer consumer = session.createConsumer(topic);
    cleanup.deferCleanup(consumer);
    consumer.setMessageListener(listener);

    listener.onMessage(message);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(CONSUMER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            messagingDestinationName(topicName, topicName),
                            oldOperation("process"),
                            operationName("process"),
                            operationType("process"),
                            messagingTempDestination(false))));
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  void doesNotReuseListenerSubscriptionNameAcrossCallbacks() throws JMSException {
    String topicName = "redelivered-message-topic";
    Topic topic = session.createTopic(topicName);
    TextMessage message = session.createTextMessage("hello there");
    message.setJMSDestination(topic);
    MessageListener durableListener = ignored -> {};
    MessageListener regularListener = ignored -> {};

    MessageConsumer durableConsumer =
        session.createDurableConsumer(topic, "redelivered-message-subscription");
    cleanup.deferCleanup(durableConsumer);
    durableConsumer.setMessageListener(durableListener);

    MessageConsumer regularConsumer = session.createConsumer(topic);
    cleanup.deferCleanup(regularConsumer);
    regularConsumer.setMessageListener(regularListener);

    durableListener.onMessage(message);
    regularListener.onMessage(message);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(CONSUMER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            messagingDestinationName(topicName, topicName),
                            oldOperation("process"),
                            operationName("process"),
                            operationType("process"),
                            messagingTempDestination(false),
                            subscriptionName("redelivered-message-subscription"))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(CONSUMER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            messagingDestinationName(topicName, topicName),
                            oldOperation("process"),
                            operationName("process"),
                            operationType("process"),
                            messagingTempDestination(false))));
  }

  @Test
  void keepsSubscriptionNameWhenListenerRegistrationFails() throws JMSException {
    String topicName = "failed-listener-registration-topic";
    Topic topic = session.createTopic(topicName);
    TextMessage message = session.createTextMessage("hello there");
    message.setJMSDestination(topic);
    MessageListener listener = ignored -> {};

    MessageConsumer registeredConsumer =
        session.createDurableConsumer(topic, "registered-subscription");
    cleanup.deferCleanup(registeredConsumer);
    registeredConsumer.setMessageListener(listener);

    MessageConsumer closedConsumer = session.createDurableConsumer(topic, "closed-subscription");
    closedConsumer.close();
    assertThatThrownBy(() -> closedConsumer.setMessageListener(listener))
        .isInstanceOf(JMSException.class);

    listener.onMessage(message);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(CONSUMER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            messagingDestinationName(topicName, topicName),
                            oldOperation("process"),
                            operationName("process"),
                            operationType("process"),
                            messagingTempDestination(false),
                            subscriptionName("registered-subscription"))));
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  void failedRegistrationDoesNotOverwriteNewerSubscriptionName() throws Exception {
    String topicName = "concurrent-registration-topic";
    Topic topic = session.createTopic(topicName);
    TextMessage message = session.createTextMessage("hello there");
    message.setJMSDestination(topic);
    MessageListener listener = ignored -> {};
    CountDownLatch olderRegistrationEntered = new CountDownLatch(1);
    CountDownLatch failOlderRegistration = new CountDownLatch(1);

    MessageConsumer olderConsumer =
        (MessageConsumer)
            Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {MessageConsumer.class},
                (proxy, method, args) -> {
                  if (method.getName().equals("setMessageListener")) {
                    olderRegistrationEntered.countDown();
                    assertThat(failOlderRegistration.await(10, SECONDS)).isTrue();
                    throw new JMSException("failed");
                  }
                  return null;
                });
    MessageConsumer newerConsumer =
        (MessageConsumer)
            Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {MessageConsumer.class},
                (proxy, method, args) -> {
                  if (method.getName().equals("setMessageListener")) {
                    ((MessageListener) args[0]).onMessage(message);
                  }
                  return null;
                });
    Session registrationSession =
        (Session)
            Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {Session.class},
                (proxy, method, args) ->
                    args[1].equals("older-subscription") ? olderConsumer : newerConsumer);
    registrationSession.createDurableConsumer(topic, "older-subscription");
    registrationSession.createDurableConsumer(topic, "newer-subscription");

    CompletableFuture<Void> failedRegistration =
        CompletableFuture.runAsync(
            () ->
                assertThatThrownBy(() -> olderConsumer.setMessageListener(listener))
                    .isInstanceOf(JMSException.class));
    try {
      assertThat(olderRegistrationEntered.await(10, SECONDS)).isTrue();
      newerConsumer.setMessageListener(listener);
    } finally {
      failOlderRegistration.countDown();
    }
    failedRegistration.get(10, SECONDS);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(CONSUMER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            messagingDestinationName(topicName, topicName),
                            oldOperation("process"),
                            operationName("process"),
                            operationType("process"),
                            messagingTempDestination(false),
                            subscriptionName("newer-subscription"))));
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  void capturesSubscriptionNameForChildClassLoaderListener() throws Exception {
    String topicName = "child-classloader-listener-topic";
    Topic topic = session.createTopic(topicName);
    TextMessage message = session.createTextMessage("hello there");
    message.setJMSDestination(topic);
    MessageConsumer consumer =
        session.createDurableConsumer(topic, "child-classloader-subscription");
    cleanup.deferCleanup(consumer);

    String listenerClassName =
        "io.opentelemetry.javaagent.instrumentation.jms.v3_0.ChildClassLoaderMessageListener";
    URLClassLoader classLoader = childFirstClassLoader(listenerClassName);
    cleanup.deferCleanup(classLoader);
    Constructor<?> constructor = classLoader.loadClass(listenerClassName).getDeclaredConstructor();
    constructor.setAccessible(true);
    MessageListener listener = (MessageListener) constructor.newInstance();
    assertThat(listener.getClass().getClassLoader()).isSameAs(classLoader);
    consumer.setMessageListener(listener);
    consumer.getMessageListener().onMessage(message);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(CONSUMER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            messagingDestinationName(topicName, topicName),
                            oldOperation("process"),
                            operationName("process"),
                            operationType("process"),
                            messagingTempDestination(false),
                            subscriptionName("child-classloader-subscription"))));
  }

  private static URLClassLoader childFirstClassLoader(String childClassName) {
    URL testClasses =
        Jms3InstrumentationTest.class.getProtectionDomain().getCodeSource().getLocation();
    return new URLClassLoader(
        new URL[] {testClasses}, Jms3InstrumentationTest.class.getClassLoader()) {
      @Override
      protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (!name.equals(childClassName)) {
          return super.loadClass(name, resolve);
        }
        synchronized (getClassLoadingLock(name)) {
          Class<?> loaded = findLoadedClass(name);
          if (loaded == null) {
            loaded = findClass(name);
          }
          if (resolve) {
            resolveClass(loaded);
          }
          return loaded;
        }
      }
    };
  }

  @ParameterizedTest
  @MethodSource("sharedReceiveConsumerArguments")
  void capturesSharedConsumerNameOnReceive(
      String subscriptionName, SharedConsumerFactory consumerFactory) throws JMSException {
    Topic topic = session.createTopic("shared-receive-topic");
    TextMessage sentMessage = session.createTextMessage("hello there");
    MessageProducer producer = session.createProducer(topic);
    cleanup.deferCleanup(producer);
    MessageConsumer consumer = consumerFactory.create(session, topic, subscriptionName);
    cleanup.deferCleanup(consumer);

    testing.runWithSpan("producer parent", () -> producer.send(sentMessage));
    TextMessage receivedMessage =
        testing.runWithSpan("consumer parent", () -> (TextMessage) consumer.receive());

    String messageId = receivedMessage.getJMSMessageID();
    AtomicReference<SpanData> producerSpan = new AtomicReference<>();
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("producer parent").hasNoParent(),
              span ->
                  span.hasKind(PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          equalTo(MESSAGING_SYSTEM, "jms"),
                          messagingDestinationName("shared-receive-topic", "shared-receive-topic"),
                          oldOperation("publish"),
                          operationName("send"),
                          operationType("send"),
                          equalTo(MESSAGING_MESSAGE_ID, messageId),
                          messagingTempDestination(false)));
          producerSpan.set(trace.getSpan(1));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("consumer parent").hasNoParent(),
                span ->
                    span.hasKind(emitStableMessagingSemconv() ? CLIENT : CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            messagingDestinationName(
                                "shared-receive-topic", "shared-receive-topic"),
                            oldOperation("receive"),
                            operationName("receive"),
                            operationType("receive"),
                            equalTo(MESSAGING_MESSAGE_ID, messageId),
                            subscriptionName(subscriptionName))));
  }

  @ParameterizedTest
  @MethodSource("sharedListenerConsumerArguments")
  void capturesSharedConsumerNameOnProviderStyleListenerDispatch(
      String subscriptionName, SharedConsumerFactory consumerFactory) throws JMSException {
    Topic topic = session.createTopic("shared-listener-topic");
    TextMessage message = session.createTextMessage("hello there");
    message.setJMSDestination(topic);
    MessageConsumer consumer = consumerFactory.create(session, topic, subscriptionName);
    cleanup.deferCleanup(consumer);
    MessageListener listener = ignored -> {};
    consumer.setMessageListener(listener);

    MessageListener providerListener = consumer.getMessageListener();
    assertThat(providerListener).isSameAs(listener);
    providerListener.onMessage(message);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "process shared-listener-topic"
                                : "shared-listener-topic process")
                        .hasKind(CONSUMER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            messagingDestinationName(
                                "shared-listener-topic", "shared-listener-topic"),
                            oldOperation("process"),
                            operationName("process"),
                            operationType("process"),
                            messagingTempDestination(false),
                            subscriptionName(subscriptionName))));
  }

  @Test
  void capturesMostRecentSubscriptionNameForReusedListener() throws JMSException {
    Topic topic = session.createTopic("reused-listener-topic");
    TextMessage message = session.createTextMessage("hello there");
    message.setJMSDestination(topic);
    MessageListener listener = ignored -> {};

    MessageConsumer consumerWithoutSubscription = session.createConsumer(topic);
    cleanup.deferCleanup(consumerWithoutSubscription);
    consumerWithoutSubscription.setMessageListener(listener);

    MessageConsumer sharedConsumer =
        session.createSharedConsumer(topic, "reused-listener-subscription");
    cleanup.deferCleanup(sharedConsumer);
    sharedConsumer.setMessageListener(listener);

    sharedConsumer.getMessageListener().onMessage(message);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(CONSUMER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            messagingDestinationName(
                                "reused-listener-topic", "reused-listener-topic"),
                            oldOperation("process"),
                            operationName("process"),
                            operationType("process"),
                            messagingTempDestination(false),
                            subscriptionName("reused-listener-subscription"))));
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @ParameterizedTest
  @MethodSource("destinationArguments")
  void testMessageConsumer(DestinationFactory destinationFactory, boolean isTemporary)
      throws JMSException {

    // given
    Destination destination = destinationFactory.create(session);
    TextMessage sentMessage = session.createTextMessage("hello there");

    MessageProducer producer = session.createProducer(destination);
    cleanup.deferCleanup(producer);
    MessageConsumer consumer = session.createConsumer(destination);
    cleanup.deferCleanup(consumer);

    // when
    testing.runWithSpan("producer parent", () -> producer.send(sentMessage));

    TextMessage receivedMessage =
        testing.runWithSpan("consumer parent", () -> (TextMessage) consumer.receive());

    // then
    assertThat(receivedMessage.getText()).isEqualTo(sentMessage.getText());

    String actualDestinationName = ((ActiveMQDestination) destination).getName();
    // artemis consumers don't know whether the destination is temporary or not
    String producerDestinationName = isTemporary ? "(temporary)" : actualDestinationName;
    String messageId = receivedMessage.getJMSMessageID();

    AtomicReference<SpanData> producerSpan = new AtomicReference<>();
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("producer parent").hasNoParent(),
              span ->
                  span.hasName(
                          emitStableMessagingSemconv()
                              ? producerDestinationName.equals("(temporary)")
                                  ? "send"
                                  : "send " + producerDestinationName
                              : producerDestinationName + " publish")
                      .hasKind(PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          equalTo(MESSAGING_SYSTEM, "jms"),
                          messagingDestinationName(producerDestinationName, actualDestinationName),
                          oldOperation("publish"),
                          operationName("send"),
                          operationType("send"),
                          equalTo(MESSAGING_MESSAGE_ID, messageId),
                          messagingTempDestination(isTemporary)));

          producerSpan.set(trace.getSpan(1));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("consumer parent").hasNoParent(),
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? actualDestinationName.equals("(temporary)")
                                    ? "receive"
                                    : "receive " + actualDestinationName
                                : actualDestinationName + " receive")
                        .hasKind(emitStableMessagingSemconv() ? CLIENT : CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            messagingDestinationName(actualDestinationName, actualDestinationName),
                            oldOperation("receive"),
                            operationName("receive"),
                            operationType("receive"),
                            equalTo(MESSAGING_MESSAGE_ID, messageId))));
  }

  private static Stream<Arguments> sharedReceiveConsumerArguments() {
    return sharedConsumerArguments("receive");
  }

  private static Stream<Arguments> sharedListenerConsumerArguments() {
    return sharedConsumerArguments("listener");
  }

  // durable subscriptions outlive the consumer that created them, so each test needs its own
  // subscription names
  private static Stream<Arguments> sharedConsumerArguments(String scenario) {
    return Stream.of(
        argumentSet(
            "shared",
            "shared-" + scenario + "-subscription",
            (SharedConsumerFactory) Session::createSharedConsumer),
        argumentSet(
            "shared durable",
            "shared-durable-" + scenario + "-subscription",
            (SharedConsumerFactory) Session::createSharedDurableConsumer));
  }

  @FunctionalInterface
  interface SharedConsumerFactory {

    MessageConsumer create(Session session, Topic topic, String subscriptionName)
        throws JMSException;
  }
}
