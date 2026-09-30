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
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.concurrent.atomic.AtomicReference;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.TextMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class Jms1SuppressReceiveSpansTest extends AbstractJms1Test {

  @Test
  void receivedMessageKeepsLegacyProcessParent() throws JMSException {
    assumeFalse(emitStableMessagingSemconv());
    Destination destination = session.createQueue("legacyProcessParentQueue");
    MessageProducer producer = session.createProducer(destination);
    cleanup.deferCleanup(producer::close);
    MessageConsumer consumer = session.createConsumer(destination);
    cleanup.deferCleanup(consumer::close);

    producer.send(session.createTextMessage("a message"));
    TextMessage receivedMessage = (TextMessage) consumer.receive();
    MessageListener listener = message -> {};
    listener.onMessage(receivedMessage);

    testing.waitForTraces(1);
    assertThat(testing.spans()).hasSize(3);
    SpanData producerSpan =
        testing.spans().stream()
            .filter(span -> span.getName().equals("legacyProcessParentQueue publish"))
            .findFirst()
            .orElseThrow(IllegalStateException::new);
    SpanData receiveSpan =
        testing.spans().stream()
            .filter(span -> span.getName().equals("legacyProcessParentQueue receive"))
            .findFirst()
            .orElseThrow(IllegalStateException::new);
    assertThat(receiveSpan.getParentSpanId()).isEqualTo(producerSpan.getSpanId());
    assertThat(testing.spans())
        .filteredOn(span -> span.getName().equals("legacyProcessParentQueue process"))
        .singleElement()
        .satisfies(
            span -> {
              assertThat(span.getParentSpanId()).isEqualTo(producerSpan.getSpanId());
              assertThat(span.getParentSpanId()).isNotEqualTo(receiveSpan.getSpanId());
            });
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

    if (emitStableMessagingSemconv()) {
      AtomicReference<SpanData> publishSpan = new AtomicReference<>();
      testing.waitAndAssertTraces(
          trace -> {
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("producer parent").hasNoParent(),
                span ->
                    span.hasName(
                            destinationName.equals("(temporary)")
                                ? "send"
                                : "send " + destinationName)
                        .hasKind(PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            messagingDestinationName(destinationName, isTemporary),
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
                              destinationName.equals("(temporary)")
                                  ? "receive"
                                  : "receive " + destinationName)
                          .hasKind(CLIENT)
                          .hasParent(trace.getSpan(0))
                          .hasLinks(LinkData.create(publishSpan.get().getSpanContext()))
                          .hasAttributesSatisfyingExactly(
                              equalTo(MESSAGING_SYSTEM, "jms"),
                              messagingDestinationName(destinationName, isTemporary),
                              equalTo(
                                  MESSAGING_OPERATION,
                                  emitOldMessagingSemconv() ? "receive" : null),
                              equalTo(
                                  MESSAGING_OPERATION_NAME,
                                  emitStableMessagingSemconv() ? "receive" : null),
                              equalTo(
                                  MESSAGING_OPERATION_TYPE,
                                  emitStableMessagingSemconv() ? "receive" : null),
                              equalTo(MESSAGING_MESSAGE_ID, messageId),
                              messagingTempDestination(isTemporary))));
      return;
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("producer parent").hasNoParent(),
                span ->
                    span.hasName(destinationName + " publish")
                        .hasKind(PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            messagingDestinationName(destinationName, isTemporary),
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
                    span.hasName(destinationName + " receive")
                        .hasKind(CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasTotalRecordedLinks(0)
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            messagingDestinationName(destinationName, isTemporary),
                            equalTo(
                                MESSAGING_OPERATION, emitOldMessagingSemconv() ? "receive" : null),
                            equalTo(
                                MESSAGING_OPERATION_NAME,
                                emitStableMessagingSemconv() ? "receive" : null),
                            equalTo(
                                MESSAGING_OPERATION_TYPE,
                                emitStableMessagingSemconv() ? "receive" : null),
                            equalTo(MESSAGING_MESSAGE_ID, messageId),
                            messagingTempDestination(isTemporary))),
        trace ->
            trace.hasSpansSatisfyingExactly(span -> span.hasName("consumer parent").hasNoParent()));
  }
}
