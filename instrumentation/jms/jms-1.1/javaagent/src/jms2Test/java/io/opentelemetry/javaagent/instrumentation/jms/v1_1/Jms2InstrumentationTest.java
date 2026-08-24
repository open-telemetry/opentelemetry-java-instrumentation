/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v1_1;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_SUBSCRIPTION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_TEMPORARY;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.io.File;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
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
import org.assertj.core.api.AbstractAssert;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.HornetQClient;
import org.hornetq.api.core.client.ServerLocator;
import org.hornetq.api.jms.HornetQJMSClient;
import org.hornetq.api.jms.JMSFactoryType;
import org.hornetq.core.config.Configuration;
import org.hornetq.core.config.CoreQueueConfiguration;
import org.hornetq.core.config.impl.ConfigurationImpl;
import org.hornetq.core.remoting.impl.invm.InVMAcceptorFactory;
import org.hornetq.core.remoting.impl.invm.InVMConnectorFactory;
import org.hornetq.core.server.HornetQServer;
import org.hornetq.core.server.HornetQServers;
import org.hornetq.jms.client.HornetQConnectionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("deprecation") // using deprecated semconv
class Jms2InstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static HornetQConnectionFactory connectionFactory;
  private static Connection connection;
  private static Session session;

  @BeforeAll
  static void setUp() throws Exception {
    File tempDir = Files.createTempDirectory("jmsTempDir").toFile();
    tempDir.deleteOnExit();

    Configuration config = new ConfigurationImpl();
    config.setBindingsDirectory(tempDir.getPath());
    config.setJournalDirectory(tempDir.getPath());
    config.setCreateBindingsDir(false);
    config.setCreateJournalDir(false);
    config.setSecurityEnabled(false);
    config.setPersistenceEnabled(false);
    config.setQueueConfigurations(
        singletonList(new CoreQueueConfiguration("someQueue", "someQueue", null, true)));
    config.setAcceptorConfigurations(
        new HashSet<>(
            singletonList(new TransportConfiguration(InVMAcceptorFactory.class.getName()))));

    HornetQServer server = HornetQServers.newHornetQServer(config);
    server.start();
    cleanup.deferAfterAll(server::stop);

    ServerLocator serverLocator =
        HornetQClient.createServerLocatorWithoutHA(
            new TransportConfiguration(InVMConnectorFactory.class.getName()));
    ClientSessionFactory sf = serverLocator.createSessionFactory();
    ClientSession clientSession = sf.createSession(false, false, false);
    clientSession.createQueue("jms.queue.someQueue", "jms.queue.someQueue", true);
    clientSession.createQueue("jms.topic.someTopic", "jms.topic.someTopic", true);
    clientSession.close();
    sf.close();
    serverLocator.close();

    connectionFactory =
        HornetQJMSClient.createConnectionFactoryWithoutHA(
            JMSFactoryType.CF, new TransportConfiguration(InVMConnectorFactory.class.getName()));
    connection = connectionFactory.createConnection();
    connection.setClientID("jms-2-test");
    connection.start();
    session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
    session.run();
    cleanup.deferAfterAll(connectionFactory::close);
    cleanup.deferAfterAll(connection);
    cleanup.deferAfterAll(session);
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  void capturesDurableConsumerName() throws JMSException {
    Topic topic = session.createTopic("someTopic");
    TextMessage sentMessage = session.createTextMessage("a message");
    MessageProducer producer = session.createProducer(topic);
    cleanup.deferCleanup(producer);
    MessageConsumer consumer = session.createDurableConsumer(topic, "durable-subscription");
    cleanup.deferCleanup(consumer);
    MessageListener listener = message -> {};
    consumer.setMessageListener(listener);
    assertThat(consumer.getMessageListener()).isSameAs(listener);
    consumer.setMessageListener(null);

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
                          messagingDestinationName("someTopic", false),
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
                            messagingDestinationName("someTopic", false),
                            oldOperation("receive"),
                            operationName("receive"),
                            operationType("receive"),
                            equalTo(MESSAGING_MESSAGE_ID, messageId),
                            subscriptionName("durable-subscription"))));
  }

  @ParameterizedTest
  @MethodSource("sharedReceiveConsumerArguments")
  void capturesSharedConsumerNameOnReceive(
      String subscriptionName, SharedConsumerFactory consumerFactory) throws JMSException {
    Topic topic = session.createTopic("someTopic");
    TextMessage sentMessage = session.createTextMessage("a message");
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
                          messagingDestinationName("someTopic", false),
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
                            messagingDestinationName("someTopic", false),
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
    Topic topic = session.createTopic("someTopic");
    TextMessage message = session.createTextMessage("a message");
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
                                ? "process someTopic"
                                : "someTopic process")
                        .hasKind(CONSUMER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            messagingDestinationName("someTopic", false),
                            oldOperation("process"),
                            operationName("process"),
                            operationType("process"),
                            messagingTempDestination(false),
                            subscriptionName(subscriptionName))));
  }

  @Test
  void capturesMostRecentSubscriptionNameForReusedListener() throws JMSException {
    Topic topic = session.createTopic("someTopic");
    TextMessage message = session.createTextMessage("a message");
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
                            messagingDestinationName("someTopic", false),
                            oldOperation("process"),
                            operationName("process"),
                            operationType("process"),
                            messagingTempDestination(false),
                            subscriptionName("reused-listener-subscription"))));
  }

  @MethodSource("destinationArguments")
  @ParameterizedTest
  void testMessageConsumer(
      DestinationFactory destinationFactory, String destinationName, boolean isTemporary)
      throws JMSException {

    // given
    Destination destination = destinationFactory.create(session);
    TextMessage sentMessage = session.createTextMessage("a message");

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

  @MethodSource("destinationArguments")
  @ParameterizedTest
  void testMessageListener(
      DestinationFactory destinationFactory, String destinationName, boolean isTemporary)
      throws Exception {

    // given
    Destination destination = destinationFactory.create(session);
    TextMessage sentMessage = session.createTextMessage("a message");

    MessageProducer producer = session.createProducer(null);
    cleanup.deferCleanup(producer);
    MessageConsumer consumer = session.createConsumer(destination);
    cleanup.deferCleanup(consumer);

    CompletableFuture<TextMessage> receivedMessageFuture = new CompletableFuture<>();
    consumer.setMessageListener(
        message ->
            testing.runWithSpan(
                "consumer", () -> receivedMessageFuture.complete((TextMessage) message)));

    // when
    testing.runWithSpan("producer parent", () -> producer.send(destination, sentMessage));

    // then
    TextMessage receivedMessage = receivedMessageFuture.get(10, SECONDS);
    assertThat(receivedMessage.getText()).isEqualTo(sentMessage.getText());

    String messageId = receivedMessage.getJMSMessageID();

    testing.waitAndAssertTraces(
        trace ->
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
                            messagingTempDestination(isTemporary)),
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? destinationName.equals("(temporary)")
                                    ? "process"
                                    : "process " + destinationName
                                : destinationName + " process")
                        .hasKind(CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            messagingDestinationName(destinationName, isTemporary),
                            oldOperation("process"),
                            operationName("process"),
                            operationType("process"),
                            equalTo(MESSAGING_MESSAGE_ID, messageId),
                            messagingTempDestination(isTemporary)),
                span -> span.hasName("consumer").hasParent(trace.getSpan(2))));
  }

  @MethodSource("emptyReceiveArguments")
  @ParameterizedTest
  void shouldNotEmitTelemetryOnEmptyReceive(
      DestinationFactory destinationFactory, MessageReceiver receiver) throws JMSException {

    // given
    Destination destination = destinationFactory.create(session);

    MessageConsumer consumer = session.createConsumer(destination);
    cleanup.deferCleanup(consumer);

    // when
    Message message = receiver.receive(consumer);

    // then
    assertThat(message).isNull();

    testing.waitForTraces(0);
  }

  private static AttributeAssertion messagingTempDestination(boolean isTemporary) {
    return isTemporary
        ? equalTo(MESSAGING_DESTINATION_TEMPORARY, true)
        : satisfies(MESSAGING_DESTINATION_TEMPORARY, AbstractAssert::isNull);
  }

  private static AttributeAssertion messagingDestinationName(
      String destinationName, boolean isTemporary) {
    return emitStableMessagingSemconv() && isTemporary
        ? satisfies(MESSAGING_DESTINATION_NAME, val -> val.isNotEmpty())
        : equalTo(MESSAGING_DESTINATION_NAME, destinationName);
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

  private static Stream<Arguments> emptyReceiveArguments() {
    DestinationFactory topic = session -> session.createTopic("someTopic");
    DestinationFactory queue = session -> session.createQueue("someQueue");
    MessageReceiver receive = consumer -> consumer.receive(100);
    MessageReceiver receiveNoWait = MessageConsumer::receiveNoWait;

    return Stream.of(
        arguments(topic, receive),
        arguments(queue, receive),
        arguments(topic, receiveNoWait),
        arguments(queue, receiveNoWait));
  }

  private static Stream<Arguments> destinationArguments() {
    DestinationFactory topic = session -> session.createTopic("someTopic");
    DestinationFactory queue = session -> session.createQueue("someQueue");
    DestinationFactory tempTopic = Session::createTemporaryTopic;
    DestinationFactory tempQueue = Session::createTemporaryQueue;

    return Stream.of(
        arguments(topic, "someTopic", false),
        arguments(queue, "someQueue", false),
        arguments(tempTopic, "(temporary)", true),
        arguments(tempQueue, "(temporary)", true));
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
  interface DestinationFactory {

    Destination create(Session session) throws JMSException;
  }

  @FunctionalInterface
  interface MessageReceiver {

    Message receive(MessageConsumer consumer) throws JMSException;
  }

  @FunctionalInterface
  interface SharedConsumerFactory {

    MessageConsumer create(Session session, Topic topic, String subscriptionName)
        throws JMSException;
  }
}
