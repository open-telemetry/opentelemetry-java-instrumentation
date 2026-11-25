/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v1_1;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_SUBSCRIPTION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_TEMPORARY;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.io.File;
import java.util.HashSet;
import java.util.stream.Stream;
import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
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
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Exercises the JMS 2.0 simplified API ({@code JMSContext} / {@code JMSProducer} / {@code
 * JMSConsumer}) against an in-VM HornetQ broker.
 *
 * <p>The point of these tests is the span *count*. HornetQ implements the simplified API on top of
 * the classic one, so without a shared call depth between the two instrumentations every send and
 * receive would produce two spans.
 *
 * <p>The same delegation is how subscription names reach the simplified API: HornetQ creates a
 * durable or shared {@code JMSConsumer} through the classic {@code Session} methods.
 */
class JmsSimplifiedApiTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static HornetQConnectionFactory connectionFactory;
  private static Connection connection;
  private static Session session;

  @BeforeAll
  static void setUp(@TempDir File tempDir) throws Exception {
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
    clientSession.createQueue("jms.queue.someNoTimeoutQueue", "jms.queue.someNoTimeoutQueue", true);
    clientSession.createQueue("jms.topic.someTopic", "jms.topic.someTopic", true);
    clientSession.close();
    sf.close();
    serverLocator.close();

    connectionFactory =
        HornetQJMSClient.createConnectionFactoryWithoutHA(
            JMSFactoryType.CF, new TransportConfiguration(InVMConnectorFactory.class.getName()));
    connection = connectionFactory.createConnection();
    connection.setClientID("jms-2-simplified-test");
    connection.start();
    session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
    session.run();
    cleanup.deferAfterAll(connectionFactory::close);
    cleanup.deferAfterAll(connection);
    cleanup.deferAfterAll(session);
  }

  @ParameterizedTest
  @MethodSource("consumerReceiveArguments")
  void producerSendAndConsumerReceiveEachEmitExactlyOneSpan(
      String queueName, JmsConsumerReceiver receiver) throws JMSException {
    Destination destination = session.createQueue(queueName);
    TextMessage sentMessage = session.createTextMessage("hello there");

    JMSContext context = connectionFactory.createContext();
    cleanup.deferCleanup(context);
    JMSConsumer consumer = context.createConsumer(destination);
    cleanup.deferCleanup(consumer);

    testing.runWithSpan(
        "producer parent", () -> context.createProducer().send(destination, sentMessage));

    Message received = testing.runWithSpan("consumer parent", () -> receiver.receive(consumer));
    assertThat(received).isNotNull();
    assertThat(((TextMessage) received).getText()).isEqualTo("hello there");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("producer parent").hasNoParent(),
                span ->
                    span.hasName("send " + queueName)
                        .hasKind(PRODUCER)
                        .hasParent(trace.getSpan(0))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("consumer parent").hasNoParent(),
                span ->
                    span.hasName("receive " + queueName)
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  void shouldNotEmitTelemetryOnEmptyReceive() throws JMSException {
    Destination destination = session.createTopic("someTopic");

    JMSContext context = connectionFactory.createContext();
    cleanup.deferCleanup(context);
    JMSConsumer consumer = context.createConsumer(destination);
    cleanup.deferCleanup(consumer);

    assertThat(consumer.receive(100)).isNull();
    assertThat(consumer.receiveNoWait()).isNull();

    testing.waitForTraces(0);
  }

  @ParameterizedTest
  @MethodSource("subscriptionConsumerArguments")
  void receiveCapturesSubscriptionName(
      String subscriptionName, JmsContextConsumerFactory consumerFactory) throws JMSException {
    Topic topic = session.createTopic("someTopic");
    TextMessage sentMessage = session.createTextMessage("hello there");

    JMSContext context = connectionFactory.createContext();
    cleanup.deferCleanup(context);
    // durable subscriptions are scoped to a client id
    context.setClientID(subscriptionName + "-client");
    JMSConsumer consumer = consumerFactory.create(context, topic, subscriptionName);
    cleanup.deferCleanup(consumer);

    testing.runWithSpan("producer parent", () -> context.createProducer().send(topic, sentMessage));

    Message received = testing.runWithSpan("consumer parent", () -> consumer.receive(10_000));
    assertThat(received).isNotNull();
    String messageId = received.getJMSMessageID();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("producer parent").hasNoParent(),
                span ->
                    span.hasName("send someTopic")
                        .hasKind(PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("messaging.system.name"), "jms"),
                            equalTo(MESSAGING_DESTINATION_NAME, "someTopic"),
                            equalTo(MESSAGING_OPERATION_NAME, "send"),
                            equalTo(MESSAGING_OPERATION_TYPE, "send"),
                            equalTo(MESSAGING_MESSAGE_ID, messageId),
                            satisfies(MESSAGING_DESTINATION_TEMPORARY, AbstractAssert::isNull))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("consumer parent").hasNoParent(),
                span ->
                    span.hasName("receive someTopic")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("messaging.system.name"), "jms"),
                            equalTo(MESSAGING_DESTINATION_NAME, "someTopic"),
                            equalTo(MESSAGING_OPERATION_NAME, "receive"),
                            equalTo(MESSAGING_OPERATION_TYPE, "receive"),
                            equalTo(MESSAGING_MESSAGE_ID, messageId),
                            equalTo(MESSAGING_DESTINATION_SUBSCRIPTION_NAME, subscriptionName))));
  }

  @ParameterizedTest
  @MethodSource("subscriptionConsumerArguments")
  void listenerCapturesSubscriptionName(
      String subscriptionName, JmsContextConsumerFactory consumerFactory) throws JMSException {
    Topic topic = session.createTopic("someTopic");
    TextMessage message = session.createTextMessage("hello there");
    message.setJMSDestination(topic);

    JMSContext context = connectionFactory.createContext();
    cleanup.deferCleanup(context);
    context.setClientID(subscriptionName + "-listener-client");
    JMSConsumer consumer = consumerFactory.create(context, topic, subscriptionName + "-listener");
    cleanup.deferCleanup(consumer);
    consumer.setMessageListener(ignored -> {});

    // HornetQ wraps the listener and registers the wrapper with its classic MessageConsumer;
    // calling the wrapper directly is what the provider does on delivery
    MessageListener providerListener = consumer.getMessageListener();
    providerListener.onMessage(message);

    testing.waitAndAssertTraces(trace ->
        trace.hasSpansSatisfyingExactly(span ->
            span.hasName("process someTopic")
                .hasKind(CONSUMER)
                .hasNoParent()
                .hasAttributesSatisfyingExactly(
                    equalTo(stringKey("messaging.system.name"), "jms"),
                    equalTo(MESSAGING_DESTINATION_NAME, "someTopic"),
                    equalTo(MESSAGING_OPERATION_NAME, "process"),
                    equalTo(MESSAGING_OPERATION_TYPE, "process"),
                    satisfies(MESSAGING_DESTINATION_TEMPORARY, AbstractAssert::isNull),
                    equalTo(
                        MESSAGING_DESTINATION_SUBSCRIPTION_NAME, subscriptionName + "-listener"))));
  }

  // durable subscriptions outlive the consumer that created them, so each case gets its own name
  private static Stream<Arguments> subscriptionConsumerArguments() {
    return Stream.of(
        argumentSet(
            "durable",
            "simplified-durable-subscription",
            (JmsContextConsumerFactory) JMSContext::createDurableConsumer),
        argumentSet(
            "shared",
            "simplified-shared-subscription",
            (JmsContextConsumerFactory) JMSContext::createSharedConsumer),
        argumentSet(
            "shared durable",
            "simplified-shared-durable-subscription",
            (JmsContextConsumerFactory) JMSContext::createSharedDurableConsumer));
  }

  // each case gets its own queue so the runs can't see each other's messages
  private static Stream<Arguments> consumerReceiveArguments() {
    JmsConsumerReceiver receiveWithTimeout = consumer -> consumer.receive(10_000);
    JmsConsumerReceiver receiveWithoutTimeout = JMSConsumer::receive;

    return Stream.of(
        argumentSet("receive(timeout)", "someQueue", receiveWithTimeout),
        argumentSet("receive()", "someNoTimeoutQueue", receiveWithoutTimeout));
  }

  @FunctionalInterface
  interface JmsConsumerReceiver {

    Message receive(JMSConsumer consumer);
  }

  @FunctionalInterface
  interface JmsContextConsumerFactory {

    JMSConsumer create(JMSContext context, Topic topic, String subscriptionName);
  }
}
