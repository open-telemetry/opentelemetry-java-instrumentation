/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v3_0;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
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
                          equalTo(MESSAGING_DESTINATION_NAME, actualDestinationName),
                          equalTo(MESSAGING_OPERATION, null),
                          equalTo(MESSAGING_OPERATION_NAME, "send"),
                          equalTo(MESSAGING_OPERATION_TYPE, "send"),
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
                            equalTo(MESSAGING_DESTINATION_NAME, actualDestinationName),
                            equalTo(MESSAGING_OPERATION, null),
                            equalTo(MESSAGING_OPERATION_NAME, "receive"),
                            equalTo(MESSAGING_OPERATION_TYPE, "receive"),
                            equalTo(MESSAGING_MESSAGE_ID, messageId))));
  }
}
