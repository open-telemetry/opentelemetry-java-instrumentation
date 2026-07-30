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
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.TextMessage;
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

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("producer parent").hasNoParent(),
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? producerDestinationName.equals("(temporary)")
                                    ? "publish"
                                    : "publish " + producerDestinationName
                                : producerDestinationName + " publish")
                        .hasKind(PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            messagingDestinationName(
                                producerDestinationName, actualDestinationName),
                            oldOperation("publish"),
                            operationName("publish"),
                            operationType("publish"),
                            equalTo(MESSAGING_MESSAGE_ID, messageId),
                            messagingTempDestination(isTemporary)),
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? actualDestinationName.equals("(temporary)")
                                    ? "receive"
                                    : "receive " + actualDestinationName
                                : actualDestinationName + " receive")
                        .hasKind(emitStableMessagingSemconv() ? CLIENT : CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasTotalRecordedLinks(0)
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            messagingDestinationName(actualDestinationName, actualDestinationName),
                            oldOperation("receive"),
                            operationName("receive"),
                            operationType("receive"),
                            equalTo(MESSAGING_MESSAGE_ID, messageId))),
        trace ->
            trace.hasSpansSatisfyingExactly(span -> span.hasName("consumer parent").hasNoParent()));
  }
}
