/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v1_1;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.testing.junit.MessagingMetricsAssertions.assertCounter;
import static io.opentelemetry.instrumentation.testing.junit.MessagingMetricsAssertions.assertHistogram;
import static io.opentelemetry.instrumentation.testing.junit.MessagingMetricsAssertions.assertNoMetric;
import static io.opentelemetry.instrumentation.testing.junit.MessagingMetricsAssertions.assertNoStableMetrics;
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
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.util.concurrent.CompletableFuture;
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
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.assertj.core.api.AbstractAssert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

@SuppressWarnings("deprecation") // using deprecated semconv
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractJms1Test {
  private static final Logger logger = LoggerFactory.getLogger(AbstractJms1Test.class);

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jms-1.1";

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  Session session;

  @BeforeAll
  void setUp() throws JMSException {
    GenericContainer<?> broker =
        new GenericContainer<>("apache/activemq-classic:5.19.2")
            .withExposedPorts(61616, 8161)
            .withLogConsumer(new Slf4jLogConsumer(logger));
    broker.start();
    cleanup.deferAfterAll(broker);

    ActiveMQConnectionFactory connectionFactory =
        new ActiveMQConnectionFactory(
            "tcp://" + broker.getHost() + ":" + broker.getMappedPort(61616));
    Connection connection = connectionFactory.createConnection();
    connection.setClientID("jms-1-test");
    connection.start();
    session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
    cleanup.deferAfterAll(connection::close);
    cleanup.deferAfterAll(session::close);
  }

  @ParameterizedTest
  @MethodSource("destinationArguments")
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

  @ParameterizedTest
  @MethodSource("emptyReceiveArguments")
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

  @ParameterizedTest
  @MethodSource("destinationArguments")
  void shouldCaptureMessageHeaders(
      DestinationFactory destinationFactory, String destinationName, boolean isTemporary)
      throws Exception {

    // given
    Destination destination = destinationFactory.create(session);
    TextMessage sentMessage = session.createTextMessage("a message");
    sentMessage.setStringProperty("Test_Message_Header", "test");
    sentMessage.setStringProperty("Uncaptured_Header", "password");
    sentMessage.setIntProperty("Test_Message_Int_Header", 1234);

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
                            messagingTempDestination(isTemporary),
                            equalTo(
                                stringArrayKey("messaging.header.Test_Message_Header"),
                                singletonList("test")),
                            equalTo(
                                stringArrayKey("messaging.header.Test_Message_Int_Header"),
                                singletonList("1234"))),
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
                            messagingTempDestination(isTemporary),
                            equalTo(
                                stringArrayKey("messaging.header.Test_Message_Header"),
                                singletonList("test")),
                            equalTo(
                                stringArrayKey("messaging.header.Test_Message_Int_Header"),
                                singletonList("1234"))),
                span -> span.hasName("consumer").hasParent(trace.getSpan(2))));
  }

  @ParameterizedTest
  @MethodSource("destinationArguments")
  void shouldFailWhenSendingReadOnlyMessage(
      DestinationFactory destinationFactory, String destinationName, boolean isTemporary)
      throws JMSException {

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
                            messagingTempDestination(isTemporary))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? destinationName.equals("(temporary)")
                                    ? "receive"
                                    : "receive " + destinationName
                                : destinationName + " receive")
                        .hasKind(emitStableMessagingSemconv() ? CLIENT : CONSUMER)
                        .hasNoParent()
                        .hasTotalRecordedLinks(0)
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            messagingDestinationName(destinationName, isTemporary),
                            oldOperation("receive"),
                            operationName("receive"),
                            operationType("receive"),
                            equalTo(MESSAGING_MESSAGE_ID, messageId),
                            messagingTempDestination(isTemporary))));
  }

  @Test
  void shouldRecordSendAndProcessMetrics() throws Exception {

    // given
    Destination destination = session.createQueue("metricsListenerQueue");
    TextMessage sentMessage = session.createTextMessage("a message");

    MessageProducer producer = session.createProducer(destination);
    cleanup.deferCleanup(producer::close);
    MessageConsumer consumer = session.createConsumer(destination);
    cleanup.deferCleanup(consumer::close);

    CompletableFuture<TextMessage> receivedMessageFuture = new CompletableFuture<>();
    consumer.setMessageListener(message -> receivedMessageFuture.complete((TextMessage) message));

    // when
    producer.send(sentMessage);

    // then
    assertThat(receivedMessageFuture.get(10, SECONDS).getText()).isEqualTo("a message");

    if (!emitStableMessagingSemconv()) {
      await().untilAsserted(() -> assertThat(testing.spans()).hasSize(2));
      assertNoStableMetrics(testing, INSTRUMENTATION_NAME);
      return;
    }

    Attributes sendAttributes = messagingMetricAttributes("send", "metricsListenerQueue");
    Attributes processAttributes = messagingMetricAttributes("process", "metricsListenerQueue");
    assertHistogram(
        testing,
        INSTRUMENTATION_NAME,
        "messaging.client.operation.duration",
        sendAttributes.toBuilder().put(MESSAGING_OPERATION_TYPE, "send").build());
    assertCounter(
        testing, INSTRUMENTATION_NAME, "messaging.client.sent.messages", 1, sendAttributes);
    assertHistogram(testing, INSTRUMENTATION_NAME, "messaging.process.duration", processAttributes);
    // A pushed message has no separate receive operation, so process owns the consumed count.
    assertCounter(
        testing, INSTRUMENTATION_NAME, "messaging.client.consumed.messages", 1, processAttributes);
  }

  @Test
  void shouldRecordReceiveMetrics() throws Exception {

    // given
    Destination destination = session.createQueue("metricsReceiveQueue");
    TextMessage sentMessage = session.createTextMessage("a message");

    MessageProducer producer = session.createProducer(destination);
    cleanup.deferCleanup(producer::close);
    MessageConsumer consumer = session.createConsumer(destination);
    cleanup.deferCleanup(consumer::close);

    // when
    producer.send(sentMessage);
    TextMessage receivedMessage = (TextMessage) consumer.receive();

    // then
    assertThat(receivedMessage.getText()).isEqualTo("a message");

    if (!emitStableMessagingSemconv()) {
      await().untilAsserted(() -> assertThat(testing.spans()).hasSize(2));
      assertNoStableMetrics(testing, INSTRUMENTATION_NAME);
      return;
    }

    Attributes receiveAttributes = messagingMetricAttributes("receive", "metricsReceiveQueue");
    assertHistogram(
        testing,
        INSTRUMENTATION_NAME,
        "messaging.client.operation.duration",
        messagingMetricAttributes("send", "metricsReceiveQueue").toBuilder()
            .put(MESSAGING_OPERATION_TYPE, "send")
            .build(),
        receiveAttributes.toBuilder().put(MESSAGING_OPERATION_TYPE, "receive").build());
    // the receive operation owns the consumed messages count whenever there is one
    assertCounter(
        testing, INSTRUMENTATION_NAME, "messaging.client.consumed.messages", 1, receiveAttributes);
    assertNoMetric(testing, INSTRUMENTATION_NAME, "messaging.process.duration");
  }

  @Test
  void shouldRecordConsumedMessagesOnceWhenReceivedMessageIsDispatchedToListener()
      throws Exception {

    // given
    Destination destination = session.createQueue("metricsReceiveAndDispatchQueue");
    TextMessage sentMessage = session.createTextMessage("a message");

    MessageProducer producer = session.createProducer(destination);
    cleanup.deferCleanup(producer::close);
    MessageConsumer consumer = session.createConsumer(destination);
    cleanup.deferCleanup(consumer::close);

    // when
    producer.send(sentMessage);
    // frameworks that poll for messages themselves dispatch them to a message listener afterwards
    Message receivedMessage = consumer.receive();
    MessageListener listener = message -> {};
    listener.onMessage(receivedMessage);

    // then
    assertThat(((TextMessage) receivedMessage).getText()).isEqualTo("a message");

    if (!emitStableMessagingSemconv()) {
      await().untilAsserted(() -> assertThat(testing.spans()).hasSize(3));
      assertNoStableMetrics(testing, INSTRUMENTATION_NAME);
      return;
    }

    assertHistogram(
        testing,
        INSTRUMENTATION_NAME,
        "messaging.process.duration",
        messagingMetricAttributes("process", "metricsReceiveAndDispatchQueue"));
    // the receive operation already counted this delivery, so the process operation must not count
    // it again
    assertCounter(
        testing,
        INSTRUMENTATION_NAME,
        "messaging.client.consumed.messages",
        1,
        messagingMetricAttributes("receive", "metricsReceiveAndDispatchQueue"));
  }

  private static Attributes messagingMetricAttributes(String operationName, String destination) {
    return Attributes.of(
        MESSAGING_OPERATION_NAME,
        operationName,
        MESSAGING_SYSTEM,
        "jms",
        MESSAGING_DESTINATION_NAME,
        destination);
  }

  static AttributeAssertion messagingTempDestination(boolean isTemporary) {
    return isTemporary
        ? equalTo(MESSAGING_DESTINATION_TEMPORARY, true)
        : satisfies(MESSAGING_DESTINATION_TEMPORARY, AbstractAssert::isNull);
  }

  static AttributeAssertion messagingDestinationName(String destinationName, boolean isTemporary) {
    return emitStableMessagingSemconv() && isTemporary
        ? satisfies(MESSAGING_DESTINATION_NAME, val -> val.isNotEmpty())
        : equalTo(MESSAGING_DESTINATION_NAME, destinationName);
  }

  static AttributeAssertion oldOperation(String operation) {
    return equalTo(MESSAGING_OPERATION, emitOldMessagingSemconv() ? operation : null);
  }

  static AttributeAssertion operationName(String operation) {
    return equalTo(MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? operation : null);
  }

  static AttributeAssertion operationType(String operation) {
    return equalTo(MESSAGING_OPERATION_TYPE, emitStableMessagingSemconv() ? operation : null);
  }

  static AttributeAssertion subscriptionName(String subscriptionName) {
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

  protected static Stream<Arguments> destinationArguments() {
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

  @FunctionalInterface
  interface DestinationFactory {

    Destination create(Session session) throws JMSException;
  }

  @FunctionalInterface
  interface MessageReceiver {

    Message receive(MessageConsumer consumer) throws JMSException;
  }
}
