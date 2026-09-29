/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v3_0;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.jms.Destination;
import jakarta.jms.JMSConsumer;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.TextMessage;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.activemq.artemis.jms.client.ActiveMQDestination;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class Jms3SuppressReceiveSpansTest extends AbstractJms3Test {

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

    if (emitStableMessagingSemconv()) {
      AtomicReference<SpanData> publishSpan = new AtomicReference<>();
      testing.waitAndAssertTraces(
          trace -> {
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("producer parent").hasNoParent(),
                span ->
                    span.hasName(
                            producerDestinationName.equals("(temporary)")
                                ? "send"
                                : "send " + producerDestinationName)
                        .hasKind(PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            messagingDestinationName(
                                producerDestinationName, actualDestinationName),
                            equalTo(
                                MESSAGING_OPERATION, emitOldMessagingSemconv() ? "publish" : null),
                            equalTo(
                                MESSAGING_OPERATION_NAME,
                                emitStableMessagingSemconv() ? "send" : null),
                            equalTo(
                                MESSAGING_OPERATION_TYPE,
                                emitStableMessagingSemconv() ? "send" : null),
                            equalTo(MESSAGING_MESSAGE_ID, messageId),
                            messagingTempDestination(isTemporary)));
            publishSpan.set(trace.getSpan(1));
          },
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("consumer parent").hasNoParent(),
                  span ->
                      span.hasName(
                              actualDestinationName.equals("(temporary)")
                                  ? "receive"
                                  : "receive " + actualDestinationName)
                          .hasKind(CLIENT)
                          .hasParent(trace.getSpan(0))
                          .hasLinks(LinkData.create(publishSpan.get().getSpanContext()))
                          .hasAttributesSatisfyingExactly(
                              equalTo(MESSAGING_SYSTEM, "jms"),
                              messagingDestinationName(
                                  actualDestinationName, actualDestinationName),
                              equalTo(
                                  MESSAGING_OPERATION,
                                  emitOldMessagingSemconv() ? "receive" : null),
                              equalTo(
                                  MESSAGING_OPERATION_NAME,
                                  emitStableMessagingSemconv() ? "receive" : null),
                              equalTo(
                                  MESSAGING_OPERATION_TYPE,
                                  emitStableMessagingSemconv() ? "receive" : null),
                              equalTo(MESSAGING_MESSAGE_ID, messageId))));
      return;
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("producer parent").hasNoParent(),
                span ->
                    span.hasName(producerDestinationName + " publish")
                        .hasKind(PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            messagingDestinationName(
                                producerDestinationName, actualDestinationName),
                            equalTo(
                                MESSAGING_OPERATION, emitOldMessagingSemconv() ? "publish" : null),
                            equalTo(
                                MESSAGING_OPERATION_NAME,
                                emitStableMessagingSemconv() ? "send" : null),
                            equalTo(
                                MESSAGING_OPERATION_TYPE,
                                emitStableMessagingSemconv() ? "send" : null),
                            equalTo(MESSAGING_MESSAGE_ID, messageId),
                            messagingTempDestination(isTemporary)),
                span ->
                    span.hasName(actualDestinationName + " receive")
                        .hasKind(CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasTotalRecordedLinks(0)
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            messagingDestinationName(actualDestinationName, actualDestinationName),
                            equalTo(
                                MESSAGING_OPERATION, emitOldMessagingSemconv() ? "receive" : null),
                            equalTo(
                                MESSAGING_OPERATION_NAME,
                                emitStableMessagingSemconv() ? "receive" : null),
                            equalTo(
                                MESSAGING_OPERATION_TYPE,
                                emitStableMessagingSemconv() ? "receive" : null),
                            equalTo(MESSAGING_MESSAGE_ID, messageId))),
        trace ->
            trace.hasSpansSatisfyingExactly(span -> span.hasName("consumer parent").hasNoParent()));
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @ParameterizedTest
  @MethodSource("jmsConsumerReceiveArguments")
  void testJmsConsumerReceive(String queueName, JmsConsumerReceiver receiver) throws JMSException {

    // given
    Destination destination = session.createQueue(queueName);
    TextMessage sentMessage = session.createTextMessage("hello there");

    MessageProducer producer = session.createProducer(destination);
    cleanup.deferCleanup(producer);

    JMSContext context = connectionFactory.createContext("test", "test");
    cleanup.deferCleanup(context);
    JMSConsumer consumer = context.createConsumer(destination);
    cleanup.deferCleanup(consumer);

    String actualDestinationName = ((ActiveMQDestination) destination).getName();

    // when
    testing.runWithSpan("producer parent", () -> producer.send(sentMessage));
    TextMessage receivedMessage =
        (TextMessage) testing.runWithSpan("consumer parent", () -> receiver.receive(consumer));

    // then
    assertThat(receivedMessage.getText()).isEqualTo(sentMessage.getText());
    String messageId = receivedMessage.getJMSMessageID();

    if (emitStableMessagingSemconv()) {
      AtomicReference<SpanData> publishSpan = new AtomicReference<>();
      testing.waitAndAssertTraces(
          trace -> {
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("producer parent").hasNoParent(),
                span ->
                    span.hasName("send " + actualDestinationName)
                        .hasKind(PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            equalTo(MESSAGING_DESTINATION_NAME, actualDestinationName),
                            equalTo(
                                MESSAGING_OPERATION, emitOldMessagingSemconv() ? "publish" : null),
                            equalTo(
                                MESSAGING_OPERATION_NAME,
                                emitStableMessagingSemconv() ? "send" : null),
                            equalTo(
                                MESSAGING_OPERATION_TYPE,
                                emitStableMessagingSemconv() ? "send" : null),
                            equalTo(MESSAGING_MESSAGE_ID, messageId),
                            messagingTempDestination(false)));
            publishSpan.set(trace.getSpan(1));
          },
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("consumer parent").hasNoParent(),
                  span ->
                      span.hasName("receive " + actualDestinationName)
                          .hasKind(CLIENT)
                          .hasParent(trace.getSpan(0))
                          .hasLinks(LinkData.create(publishSpan.get().getSpanContext()))
                          .hasAttributesSatisfyingExactly(
                              equalTo(MESSAGING_SYSTEM, "jms"),
                              equalTo(MESSAGING_DESTINATION_NAME, actualDestinationName),
                              equalTo(
                                  MESSAGING_OPERATION,
                                  emitOldMessagingSemconv() ? "receive" : null),
                              equalTo(
                                  MESSAGING_OPERATION_NAME,
                                  emitStableMessagingSemconv() ? "receive" : null),
                              equalTo(
                                  MESSAGING_OPERATION_TYPE,
                                  emitStableMessagingSemconv() ? "receive" : null),
                              equalTo(MESSAGING_MESSAGE_ID, messageId))));
      return;
    }

    // with receive spans suppressed the receive stays in the producer's trace, parented to the
    // publish span, and carries no link
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("producer parent").hasNoParent(),
                span ->
                    span.hasName(actualDestinationName + " publish")
                        .hasKind(PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            equalTo(MESSAGING_DESTINATION_NAME, actualDestinationName),
                            equalTo(
                                MESSAGING_OPERATION, emitOldMessagingSemconv() ? "publish" : null),
                            equalTo(
                                MESSAGING_OPERATION_NAME,
                                emitStableMessagingSemconv() ? "send" : null),
                            equalTo(
                                MESSAGING_OPERATION_TYPE,
                                emitStableMessagingSemconv() ? "send" : null),
                            equalTo(MESSAGING_MESSAGE_ID, messageId),
                            messagingTempDestination(false)),
                span ->
                    span.hasName(actualDestinationName + " receive")
                        .hasKind(CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasTotalRecordedLinks(0)
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            equalTo(MESSAGING_DESTINATION_NAME, actualDestinationName),
                            equalTo(
                                MESSAGING_OPERATION, emitOldMessagingSemconv() ? "receive" : null),
                            equalTo(
                                MESSAGING_OPERATION_NAME,
                                emitStableMessagingSemconv() ? "receive" : null),
                            equalTo(
                                MESSAGING_OPERATION_TYPE,
                                emitStableMessagingSemconv() ? "receive" : null),
                            equalTo(MESSAGING_MESSAGE_ID, messageId))),
        trace ->
            trace.hasSpansSatisfyingExactly(span -> span.hasName("consumer parent").hasNoParent()));
  }
}
