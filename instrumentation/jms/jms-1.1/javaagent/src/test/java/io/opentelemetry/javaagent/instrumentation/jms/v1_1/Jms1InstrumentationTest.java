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
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
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

  @SuppressWarnings("deprecation") // using deprecated JMS and semconv APIs
  @Test
  void restoresSubscriptionNameAfterFailedListenerRegistration() throws JMSException {
    Topic topic = session.createTopic("failed-listener-registration-topic");
    TextMessage message = session.createTextMessage("a message");
    message.setJMSDestination(topic);
    MessageListener listener = ignored -> {};

    MessageConsumer previousConsumer =
        session.createDurableSubscriber(topic, "previous-listener-subscription");
    cleanup.deferCleanup(previousConsumer::close);
    previousConsumer.setMessageListener(listener);

    MessageConsumer failingConsumer =
        session.createDurableSubscriber(topic, "failed-listener-subscription");
    failingConsumer.close();
    assertThatThrownBy(() -> failingConsumer.setMessageListener(listener))
        .isInstanceOf(JMSException.class);

    previousConsumer.getMessageListener().onMessage(message);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(CONSUMER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            messagingDestinationName("failed-listener-registration-topic", false),
                            oldOperation("process"),
                            operationName("process"),
                            operationType("process"),
                            messagingTempDestination(false),
                            subscriptionName("previous-listener-subscription"))));
  }

  @SuppressWarnings("deprecation") // using deprecated JMS and semconv APIs
  @ParameterizedTest
  @MethodSource("listenerRegistrationRemovalArguments")
  void restoresPreviousSubscriptionNameWhenRegistrationRemoved(
      String scenario, ConsumerRegistrationRemover registrationRemover) throws JMSException {
    String topicName = "removed-listener-registration-" + scenario;
    Topic topic = session.createTopic(topicName);
    TextMessage message = session.createTextMessage("a message");
    message.setJMSDestination(topic);
    MessageListener listener = ignored -> {};

    String previousSubscription = "previous-listener-subscription-" + scenario;
    MessageConsumer previousConsumer = session.createDurableSubscriber(topic, previousSubscription);
    cleanup.deferCleanup(previousConsumer::close);
    previousConsumer.setMessageListener(listener);

    MessageConsumer currentConsumer =
        session.createDurableSubscriber(topic, "current-listener-subscription-" + scenario);
    cleanup.deferCleanup(currentConsumer::close);
    currentConsumer.setMessageListener(listener);
    registrationRemover.remove(currentConsumer);

    previousConsumer.getMessageListener().onMessage(message);

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
                            subscriptionName(previousSubscription))));
  }

  @SuppressWarnings("deprecation") // using deprecated JMS and semconv APIs
  @ParameterizedTest
  @MethodSource("parentResourceCloseArguments")
  void restoresPreviousSubscriptionNameWhenParentResourceClosed(
      String scenario, ParentResourceCloser resourceCloser) throws JMSException {
    String topicName = "closed-parent-resource-" + scenario;
    Topic topic = session.createTopic(topicName);
    TextMessage message = session.createTextMessage("a message");
    message.setJMSDestination(topic);
    MessageListener listener = ignored -> {};

    String previousSubscription = "previous-parent-subscription-" + scenario;
    MessageConsumer previousConsumer = session.createDurableSubscriber(topic, previousSubscription);
    cleanup.deferCleanup(previousConsumer::close);
    previousConsumer.setMessageListener(listener);

    Connection currentConnection = connectionFactory.createConnection();
    cleanup.deferCleanup(currentConnection::close);
    currentConnection.setClientID("jms-1-parent-close-" + scenario);
    currentConnection.start();
    Session currentSession = currentConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
    MessageConsumer currentConsumer =
        currentSession.createDurableSubscriber(
            currentSession.createTopic(topicName), "current-parent-subscription-" + scenario);
    currentConsumer.setMessageListener(listener);

    resourceCloser.close(currentConnection, currentSession);
    previousConsumer.getMessageListener().onMessage(message);

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
                            subscriptionName(previousSubscription))));
  }

  @Test
  void suppressesReentrantListenerRegistration() {
    MessageListener listener = ignored -> {};
    ReentrantMessageConsumer consumer = new ReentrantMessageConsumer();
    JmsSubscriptionNames.set(consumer, "reentrant-listener-subscription");

    consumer.setMessageListener(listener);
    assertThat(JmsSubscriptionNames.get(listener)).isEqualTo("reentrant-listener-subscription");

    consumer.close();
    assertThat(JmsSubscriptionNames.get(listener)).isNull();
  }

  @Test
  void doesNotSuppressNestedListenerRegistrationForDifferentConsumer() {
    MessageListener listener = ignored -> {};
    TestMessageConsumer delegate = new TestMessageConsumer();
    DelegatingMessageConsumer consumer = new DelegatingMessageConsumer(delegate);
    JmsSubscriptionNames.set(consumer, "outer-listener-subscription");
    JmsSubscriptionNames.set(delegate, "delegate-listener-subscription");

    consumer.setMessageListener(listener);
    assertThat(JmsSubscriptionNames.get(listener)).isEqualTo("delegate-listener-subscription");

    delegate.close();
    assertThat(JmsSubscriptionNames.get(listener)).isEqualTo("outer-listener-subscription");

    consumer.close();
    assertThat(JmsSubscriptionNames.get(listener)).isNull();
  }

  @Test
  void doesNotCommitListenerRegistrationAfterConcurrentClose() throws Exception {
    String topicName = "concurrently-closed-consumer-topic";
    Topic topic = session.createTopic(topicName);
    TextMessage message = session.createTextMessage("a message");
    message.setJMSDestination(topic);
    MessageListener listener = ignored -> {};

    String previousSubscription = "previous-concurrent-subscription";
    MessageConsumer previousConsumer = session.createDurableSubscriber(topic, previousSubscription);
    cleanup.deferCleanup(previousConsumer::close);
    previousConsumer.setMessageListener(listener);

    BlockingMessageConsumer consumer = new BlockingMessageConsumer();
    CompletableFuture<Void> registration;
    synchronized (listener) {
      registration = CompletableFuture.runAsync(() -> consumer.setMessageListener(listener));
      assertThat(consumer.registrationStarted.await(10, SECONDS)).isTrue();
      consumer.close();
    }
    consumer.continueRegistration.release();
    registration.join();

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
                            subscriptionName(previousSubscription))));
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

  private static Stream<Arguments> listenerRegistrationRemovalArguments() {
    return Stream.of(
        argumentSet(
            "null listener",
            "null",
            (ConsumerRegistrationRemover) consumer -> consumer.setMessageListener(null)),
        argumentSet(
            "replacement listener",
            "replacement",
            (ConsumerRegistrationRemover) consumer -> consumer.setMessageListener(ignored -> {})),
        argumentSet(
            "closed consumer", "close", (ConsumerRegistrationRemover) MessageConsumer::close));
  }

  private static Stream<Arguments> parentResourceCloseArguments() {
    return Stream.of(
        argumentSet(
            "session", "session", (ParentResourceCloser) (connection, session) -> session.close()),
        argumentSet(
            "connection",
            "connection",
            (ParentResourceCloser) (connection, session) -> connection.close()));
  }

  @FunctionalInterface
  interface ConsumerRegistrationRemover {

    void remove(MessageConsumer consumer) throws JMSException;
  }

  @FunctionalInterface
  interface ParentResourceCloser {

    void close(Connection connection, Session session) throws JMSException;
  }

  private static class TestMessageConsumer implements MessageConsumer {
    private MessageListener listener;

    @Override
    public String getMessageSelector() {
      return null;
    }

    @Override
    public MessageListener getMessageListener() {
      return listener;
    }

    @Override
    public void setMessageListener(MessageListener listener) {
      this.listener = listener;
    }

    @Override
    public Message receive() {
      return null;
    }

    @Override
    public Message receive(long timeout) {
      return null;
    }

    @Override
    public Message receiveNoWait() {
      return null;
    }

    @Override
    public void close() {}
  }

  private static final class ReentrantMessageConsumer extends TestMessageConsumer {

    @Override
    @SuppressWarnings("RedundantOverride")
    public void setMessageListener(MessageListener listener) {
      super.setMessageListener(listener);
    }
  }

  private static final class DelegatingMessageConsumer extends TestMessageConsumer {
    private final TestMessageConsumer delegate;

    private DelegatingMessageConsumer(TestMessageConsumer delegate) {
      this.delegate = delegate;
    }

    @Override
    public void setMessageListener(MessageListener listener) {
      delegate.setMessageListener(listener);
    }
  }

  private static final class BlockingMessageConsumer extends TestMessageConsumer {
    private final CountDownLatch registrationStarted = new CountDownLatch(1);
    private final Semaphore continueRegistration = new Semaphore(0);

    @Override
    public void setMessageListener(MessageListener listener) {
      registrationStarted.countDown();
      continueRegistration.acquireUninterruptibly();
    }
  }
}
