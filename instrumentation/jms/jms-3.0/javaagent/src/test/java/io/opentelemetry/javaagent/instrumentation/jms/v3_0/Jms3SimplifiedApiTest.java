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
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.util.Collections.emptyEnumeration;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import jakarta.jms.Destination;
import jakarta.jms.JMSConsumer;
import jakarta.jms.JMSException;
import jakarta.jms.JMSProducer;
import jakarta.jms.Message;
import jakarta.jms.Queue;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests the {@code JMSProducer} / {@code JMSConsumer} instrumentation in isolation.
 *
 * <p>Real providers implement the simplified API by delegating to the classic one, and the classic
 * advice emits an identical span in the same instrumentation module, so a test against a real
 * provider passes even without this instrumentation. The producer and consumer used here never
 * touch a {@code MessageProducer} or {@code MessageConsumer}, so a span can only come from the
 * simplified-API advice.
 */
@SuppressWarnings("deprecation") // using deprecated semconv
class Jms3SimplifiedApiTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final String QUEUE_NAME = "stubQueue";
  private static final String MESSAGE_ID = "ID:stub-message";

  @SuppressWarnings("DirectInvocationOnMock")
  @Test
  void sendEmitsExactlyOneProducerSpan() throws JMSException {
    JMSProducer producer = mock(JMSProducer.class);

    Destination queue = queue();
    // a message that has not been sent has no destination yet, so the send argument is used
    Message message = message(null);

    testing.runWithSpan("parent", () -> producer.send(queue, message));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "send " + QUEUE_NAME
                                : QUEUE_NAME + " publish")
                        .hasKind(PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfying(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            equalTo(MESSAGING_DESTINATION_NAME, QUEUE_NAME),
                            equalTo(MESSAGING_MESSAGE_ID, MESSAGE_ID))));
  }

  @ParameterizedTest
  @MethodSource("receivers")
  void receiveEmitsExactlyOneReceiveSpan(Function<JMSConsumer, Message> receiver)
      throws JMSException {
    JMSConsumer consumer = mock(JMSConsumer.class);
    Message message = message(queue());
    when(receiver.apply(consumer)).thenReturn(message);

    testing.runWithSpan("consumer parent", () -> receiver.apply(consumer));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("consumer parent").hasNoParent(),
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "receive " + QUEUE_NAME
                                : QUEUE_NAME + " receive")
                        .hasKind(emitStableMessagingSemconv() ? CLIENT : CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfying(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            equalTo(MESSAGING_DESTINATION_NAME, QUEUE_NAME),
                            equalTo(MESSAGING_MESSAGE_ID, MESSAGE_ID))));
  }

  @ParameterizedTest
  @MethodSource("receivers")
  void emptyReceiveEmitsNoSpan(Function<JMSConsumer, Message> receiver) {
    JMSConsumer consumer = mock(JMSConsumer.class);

    testing.runWithSpan("consumer parent", () -> receiver.apply(consumer));

    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("consumer parent")));
  }

  private static Stream<Arguments> receivers() {
    return Stream.of(
        argumentSet("receive()", (Function<JMSConsumer, Message>) JMSConsumer::receive),
        argumentSet(
            "receive(timeout)", (Function<JMSConsumer, Message>) consumer -> consumer.receive(100)),
        argumentSet(
            "receiveNoWait()", (Function<JMSConsumer, Message>) JMSConsumer::receiveNoWait));
  }

  private static Destination queue() throws JMSException {
    Queue queue = mock(Queue.class);
    when(queue.getQueueName()).thenReturn(QUEUE_NAME);
    return queue;
  }

  private static Message message(Destination destination) throws JMSException {
    Message message = mock(Message.class);
    when(message.getJMSMessageID()).thenReturn(MESSAGE_ID);
    when(message.getJMSDestination()).thenReturn(destination);
    when(message.getPropertyNames()).thenReturn(emptyEnumeration());
    return message;
  }
}
