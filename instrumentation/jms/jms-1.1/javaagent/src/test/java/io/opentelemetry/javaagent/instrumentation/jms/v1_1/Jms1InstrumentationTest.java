/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v1_1;

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

import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class Jms1InstrumentationTest extends AbstractJms1Test {

  @SuppressWarnings("deprecation") // using deprecated JMS and semconv APIs
  @Test
  void capturesDurableSubscriberName() throws Exception {
    Topic topic = session.createTopic("durable-topic");
    TextMessage sentMessage = session.createTextMessage("a message");
    MessageProducer producer = session.createProducer(topic);
    cleanup.deferCleanup(producer::close);
    MessageConsumer consumer = session.createDurableSubscriber(topic, "durable-subscription");
    cleanup.deferCleanup(consumer::close);

    CompletableFuture<TextMessage> receivedMessage = new CompletableFuture<>();
    testing.runWithSpan("parent", () -> producer.send(sentMessage));
    MessageListener listener = message -> receivedMessage.complete((TextMessage) message);
    consumer.setMessageListener(listener);
    assertThat(consumer.getMessageListener()).isSameAs(listener);

    String messageId = receivedMessage.get(10, SECONDS).getJMSMessageID();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasKind(PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            messagingDestinationName("durable-topic", false),
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
                            messagingDestinationName("durable-topic", false),
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
    TextMessage message = session.createTextMessage("a message");
    message.setJMSDestination(topic);
    MessageListener listener = ignored -> {};

    MessageConsumer durableConsumer =
        session.createDurableSubscriber(topic, "reregistered-subscription");
    cleanup.deferCleanup(durableConsumer::close);
    durableConsumer.setMessageListener(listener);

    MessageConsumer consumer = session.createConsumer(topic);
    cleanup.deferCleanup(consumer::close);
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
                            messagingDestinationName(topicName, false),
                            oldOperation("process"),
                            operationName("process"),
                            operationType("process"),
                            messagingTempDestination(false))));
  }

  @SuppressWarnings("deprecation") // using deprecated JMS and semconv APIs
  @Test
  void doesNotReuseListenerSubscriptionNameAcrossCallbacks() throws JMSException {
    String topicName = "redelivered-message-topic";
    Topic topic = session.createTopic(topicName);
    TextMessage message = session.createTextMessage("a message");
    message.setJMSDestination(topic);
    MessageListener durableListener = ignored -> {};
    MessageListener regularListener = ignored -> {};

    MessageConsumer durableConsumer =
        session.createDurableSubscriber(topic, "redelivered-message-subscription");
    cleanup.deferCleanup(durableConsumer::close);
    durableConsumer.setMessageListener(durableListener);

    MessageConsumer regularConsumer = session.createConsumer(topic);
    cleanup.deferCleanup(regularConsumer::close);
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
                            messagingDestinationName(topicName, false),
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
                            messagingDestinationName(topicName, false),
                            oldOperation("process"),
                            operationName("process"),
                            operationType("process"),
                            messagingTempDestination(false))));
  }

  @SuppressWarnings("deprecation") // using deprecated JMS and semconv APIs
  @Test
  void keepsSubscriptionNameWhenListenerRegistrationFails() throws JMSException {
    String topicName = "failed-listener-registration-topic";
    Topic topic = session.createTopic(topicName);
    TextMessage message = session.createTextMessage("a message");
    message.setJMSDestination(topic);
    MessageListener listener = ignored -> {};

    MessageConsumer registeredConsumer =
        session.createDurableSubscriber(topic, "registered-subscription");
    cleanup.deferCleanup(registeredConsumer::close);
    registeredConsumer.setMessageListener(listener);

    MessageConsumer closedConsumer = session.createDurableSubscriber(topic, "closed-subscription");
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
                            messagingDestinationName(topicName, false),
                            oldOperation("process"),
                            operationName("process"),
                            operationType("process"),
                            messagingTempDestination(false),
                            subscriptionName("registered-subscription"))));
  }

  @SuppressWarnings("deprecation") // using deprecated JMS and semconv APIs
  @Test
  void failedRegistrationDoesNotOverwriteNewerSubscriptionName() throws Exception {
    String topicName = "concurrent-registration-topic";
    Topic topic = session.createTopic(topicName);
    TextMessage message = session.createTextMessage("a message");
    message.setJMSDestination(topic);
    MessageListener listener = ignored -> {};
    CountDownLatch olderRegistrationEntered = new CountDownLatch(1);
    CountDownLatch failOlderRegistration = new CountDownLatch(1);

    MessageConsumer olderConsumer =
        (MessageConsumer)
            Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {TopicSubscriber.class},
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
                new Class<?>[] {TopicSubscriber.class},
                (proxy, method, args) -> null);
    Session registrationSession =
        (Session)
            Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {Session.class},
                (proxy, method, args) ->
                    args[1].equals("older-subscription") ? olderConsumer : newerConsumer);
    registrationSession.createDurableSubscriber(topic, "older-subscription");
    registrationSession.createDurableSubscriber(topic, "newer-subscription");

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

    listener.onMessage(message);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(CONSUMER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            messagingDestinationName(topicName, false),
                            oldOperation("process"),
                            operationName("process"),
                            operationType("process"),
                            messagingTempDestination(false),
                            subscriptionName("newer-subscription"))));
  }

  @SuppressWarnings("deprecation") // using deprecated JMS and semconv APIs
  @Test
  void capturesSubscriptionNameForChildClassLoaderListener() throws Exception {
    String topicName = "child-classloader-listener-topic";
    Topic topic = session.createTopic(topicName);
    TextMessage message = session.createTextMessage("a message");
    message.setJMSDestination(topic);
    MessageConsumer consumer =
        session.createDurableSubscriber(topic, "child-classloader-subscription");
    cleanup.deferCleanup(consumer::close);

    String listenerClassName =
        "io.opentelemetry.javaagent.instrumentation.jms.v1_1.ChildClassLoaderMessageListener";
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
                            messagingDestinationName(topicName, false),
                            oldOperation("process"),
                            operationName("process"),
                            operationType("process"),
                            messagingTempDestination(false),
                            subscriptionName("child-classloader-subscription"))));
  }

  private static URLClassLoader childFirstClassLoader(String childClassName) {
    URL testClasses =
        Jms1InstrumentationTest.class.getProtectionDomain().getCodeSource().getLocation();
    return new URLClassLoader(
        new URL[] {testClasses}, Jms1InstrumentationTest.class.getClassLoader()) {
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

  @SuppressWarnings("deprecation") // using deprecated semconv
  @ParameterizedTest
  @MethodSource("destinationArguments")
  void testMessageConsumer(
      DestinationFactory destinationFactory, String destinationName, boolean isTemporary)
      throws JMSException {

    // given
    Destination destination = destinationFactory.create(session);
    TextMessage sentMessage = session.createTextMessage("a message");

    MessageProducer producer = session.createProducer(destination);
    cleanup.deferCleanup(producer::close);
    MessageConsumer consumer = session.createConsumer(destination);
    cleanup.deferCleanup(consumer::close);

    // when
    testing.runWithSpan("producer parent", () -> producer.send(sentMessage));

    TextMessage receivedMessage =
        testing.runWithSpan("consumer parent", () -> (TextMessage) consumer.receive());

    // then
    assertThat(receivedMessage.getText()).isEqualTo(sentMessage.getText());

    String messageId = receivedMessage.getJMSMessageID();

    AtomicReference<SpanData> producerSpan = new AtomicReference<>();
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("producer parent").hasNoParent(),
              span ->
                  span.hasName(
                          emitStableMessagingSemconv()
                              ? destinationName.equals("(temporary)")
                                  ? "send"
                                  : "send " + destinationName
                              : destinationName + " publish")
                      .hasKind(PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          equalTo(MESSAGING_SYSTEM, "jms"),
                          messagingDestinationName(destinationName, isTemporary),
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
                                ? destinationName.equals("(temporary)")
                                    ? "receive"
                                    : "receive " + destinationName
                                : destinationName + " receive")
                        .hasKind(emitStableMessagingSemconv() ? CLIENT : CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            messagingDestinationName(destinationName, isTemporary),
                            oldOperation("receive"),
                            operationName("receive"),
                            operationType("receive"),
                            equalTo(MESSAGING_MESSAGE_ID, messageId),
                            messagingTempDestination(isTemporary))));
  }
}
