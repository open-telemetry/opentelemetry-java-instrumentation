/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v1_1;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.assertj.core.api.AbstractAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

public class Jms1InstrumentationTest {

  static final Logger logger = LoggerFactory.getLogger(Jms1InstrumentationTest.class);

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  static GenericContainer<?> broker;
  static ActiveMQConnectionFactory connectionFactory;
  static Connection connection;
  static Session session;

  @BeforeAll
  static void setUp() throws JMSException {
    broker =
        new GenericContainer<>("rmohr/activemq:latest")
            .withExposedPorts(61616, 8161)
            .withLogConsumer(new Slf4jLogConsumer(logger));
    broker.start();

    connectionFactory =
        new ActiveMQConnectionFactory("tcp://localhost:" + broker.getMappedPort(61616));
    Connection connection = connectionFactory.createConnection();
    connection.start();
    session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
  }

  @AfterAll
  static void tearDown() throws JMSException {
    if (session != null) {
      session.close();
    }
    if (connection != null) {
      connection.close();
    }
    if (broker != null) {
      broker.close();
    }
  }

  @ArgumentsSource(DestinationsProvider.class)
  @ParameterizedTest
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
                  span.hasName(destinationName + " publish")
                      .hasKind(PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          equalTo(SemanticAttributes.MESSAGING_SYSTEM, "jms"),
                          equalTo(SemanticAttributes.MESSAGING_DESTINATION_NAME, destinationName),
                          equalTo(SemanticAttributes.MESSAGING_MESSAGE_ID, messageId),
                          messagingTempDestination(isTemporary)));

          producerSpan.set(trace.getSpan(1));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("consumer parent").hasNoParent(),
                span ->
                    span.hasName(destinationName + " receive")
                        .hasKind(CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.MESSAGING_SYSTEM, "jms"),
                            equalTo(SemanticAttributes.MESSAGING_DESTINATION_NAME, destinationName),
                            equalTo(SemanticAttributes.MESSAGING_OPERATION, "receive"),
                            equalTo(SemanticAttributes.MESSAGING_MESSAGE_ID, messageId),
                            messagingTempDestination(isTemporary))));
  }

  @ArgumentsSource(DestinationsProvider.class)
  @ParameterizedTest
  void testMessageListener(
      DestinationFactory destinationFactory, String destinationName, boolean isTemporary)
      throws Exception {

    // given
    Destination destination = destinationFactory.create(session);
    TextMessage sentMessage = session.createTextMessage("a message");

    MessageProducer producer = session.createProducer(null);
    cleanup.deferCleanup(producer::close);
    MessageConsumer consumer = session.createConsumer(destination);
    cleanup.deferCleanup(consumer::close);

    CompletableFuture<TextMessage> receivedMessageFuture = new CompletableFuture<>();
    consumer.setMessageListener(
        message ->
            testing.runWithSpan(
                "consumer", () -> receivedMessageFuture.complete((TextMessage) message)));

    // when
    testing.runWithSpan("producer parent", () -> producer.send(destination, sentMessage));

    // then
    TextMessage receivedMessage = receivedMessageFuture.get(10, TimeUnit.SECONDS);
    assertThat(receivedMessage.getText()).isEqualTo(sentMessage.getText());

    String messageId = receivedMessage.getJMSMessageID();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("producer parent").hasNoParent(),
                span ->
                    span.hasName(destinationName + " publish")
                        .hasKind(PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.MESSAGING_SYSTEM, "jms"),
                            equalTo(SemanticAttributes.MESSAGING_DESTINATION_NAME, destinationName),
                            equalTo(SemanticAttributes.MESSAGING_MESSAGE_ID, messageId),
                            messagingTempDestination(isTemporary)),
                span ->
                    span.hasName(destinationName + " process")
                        .hasKind(CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.MESSAGING_SYSTEM, "jms"),
                            equalTo(SemanticAttributes.MESSAGING_DESTINATION_NAME, destinationName),
                            equalTo(SemanticAttributes.MESSAGING_OPERATION, "process"),
                            equalTo(SemanticAttributes.MESSAGING_MESSAGE_ID, messageId),
                            messagingTempDestination(isTemporary)),
                span -> span.hasName("consumer").hasParent(trace.getSpan(2))));
  }

  @ArgumentsSource(EmptyReceiveArgumentsProvider.class)
  @ParameterizedTest
  void shouldNotEmitTelemetryOnEmptyReceive(
      DestinationFactory destinationFactory, MessageReceiver receiver) throws JMSException {

    // given
    Destination destination = destinationFactory.create(session);

    MessageConsumer consumer = session.createConsumer(destination);
    cleanup.deferCleanup(consumer::close);

    // when
    Message message = receiver.receive(consumer);

    // then
    assertThat(message).isNull();

    testing.waitForTraces(0);
  }

  @ArgumentsSource(DestinationsProvider.class)
  @ParameterizedTest
  void shouldCaptureMessageHeaders(
      DestinationFactory destinationFactory, String destinationName, boolean isTemporary)
      throws Exception {

    // given
    Destination destination = destinationFactory.create(session);
    TextMessage sentMessage = session.createTextMessage("a message");
    sentMessage.setStringProperty("test_message_header", "test");
    sentMessage.setIntProperty("test_message_int_header", 1234);

    MessageProducer producer = session.createProducer(destination);
    cleanup.deferCleanup(producer::close);
    MessageConsumer consumer = session.createConsumer(destination);
    cleanup.deferCleanup(consumer::close);

    CompletableFuture<TextMessage> receivedMessageFuture = new CompletableFuture<>();
    consumer.setMessageListener(
        message ->
            testing.runWithSpan(
                "consumer", () -> receivedMessageFuture.complete((TextMessage) message)));

    // when
    testing.runWithSpan("producer parent", () -> producer.send(sentMessage));

    // then
    TextMessage receivedMessage = receivedMessageFuture.get(10, TimeUnit.SECONDS);
    assertThat(receivedMessage.getText()).isEqualTo(sentMessage.getText());

    String messageId = receivedMessage.getJMSMessageID();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("producer parent").hasNoParent(),
                span ->
                    span.hasName(destinationName + " publish")
                        .hasKind(PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.MESSAGING_SYSTEM, "jms"),
                            equalTo(SemanticAttributes.MESSAGING_DESTINATION_NAME, destinationName),
                            equalTo(SemanticAttributes.MESSAGING_MESSAGE_ID, messageId),
                            messagingTempDestination(isTemporary),
                            equalTo(
                                stringArrayKey("messaging.header.test_message_header"),
                                singletonList("test")),
                            equalTo(
                                stringArrayKey("messaging.header.test_message_int_header"),
                                singletonList("1234"))),
                span ->
                    span.hasName(destinationName + " process")
                        .hasKind(CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.MESSAGING_SYSTEM, "jms"),
                            equalTo(SemanticAttributes.MESSAGING_DESTINATION_NAME, destinationName),
                            equalTo(SemanticAttributes.MESSAGING_OPERATION, "process"),
                            equalTo(SemanticAttributes.MESSAGING_MESSAGE_ID, messageId),
                            messagingTempDestination(isTemporary),
                            equalTo(
                                stringArrayKey("messaging.header.test_message_header"),
                                singletonList("test")),
                            equalTo(
                                stringArrayKey("messaging.header.test_message_int_header"),
                                singletonList("1234"))),
                span -> span.hasName("consumer").hasParent(trace.getSpan(2))));
  }

  @ArgumentsSource(DestinationsProvider.class)
  @ParameterizedTest
  void shouldFailWhenSendingReadOnlyMessage(
      DestinationFactory destinationFactory, String destinationName, boolean isTemporary)
      throws Exception {

    // given
    Destination destination = destinationFactory.create(session);
    ActiveMQTextMessage sentMessage = (ActiveMQTextMessage) session.createTextMessage("a message");

    MessageProducer producer = session.createProducer(destination);
    cleanup.deferCleanup(producer::close);
    MessageConsumer consumer = session.createConsumer(destination);
    cleanup.deferCleanup(consumer::close);

    sentMessage.setReadOnlyProperties(true);

    // when
    testing.runWithSpan("producer parent", () -> producer.send(sentMessage));

    TextMessage receivedMessage = (TextMessage) consumer.receive();

    // then
    assertThat(receivedMessage.getText()).isEqualTo(sentMessage.getText());

    String messageId = receivedMessage.getJMSMessageID();

    // This will result in a logged failure because we tried to
    // write properties in MessagePropertyTextMap when readOnlyProperties = true.
    // As a result, the consumer span will not be linked to the producer span as we are unable to
    // propagate the trace context as a message property.
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("producer parent").hasNoParent(),
                span ->
                    span.hasName(destinationName + " publish")
                        .hasKind(PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.MESSAGING_SYSTEM, "jms"),
                            equalTo(SemanticAttributes.MESSAGING_DESTINATION_NAME, destinationName),
                            equalTo(SemanticAttributes.MESSAGING_MESSAGE_ID, messageId),
                            messagingTempDestination(isTemporary))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(destinationName + " receive")
                        .hasKind(CONSUMER)
                        .hasNoParent()
                        .hasTotalRecordedLinks(0)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.MESSAGING_SYSTEM, "jms"),
                            equalTo(SemanticAttributes.MESSAGING_DESTINATION_NAME, destinationName),
                            equalTo(SemanticAttributes.MESSAGING_OPERATION, "receive"),
                            equalTo(SemanticAttributes.MESSAGING_MESSAGE_ID, messageId),
                            messagingTempDestination(isTemporary))));
  }

  private static AttributeAssertion messagingTempDestination(boolean isTemporary) {
    return isTemporary
        ? equalTo(SemanticAttributes.MESSAGING_DESTINATION_TEMPORARY, true)
        : satisfies(SemanticAttributes.MESSAGING_DESTINATION_TEMPORARY, AbstractAssert::isNull);
  }

  static final class EmptyReceiveArgumentsProvider implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
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
  }

  static final class DestinationsProvider implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
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
  }

  @FunctionalInterface
  interface DestinationFactory {

    Destination create(Session session) throws JMSException;
  }

  @FunctionalInterface
  interface MessageReceiver {

    Message receive(MessageConsumer consumer) throws JMSException;
  }
}
