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

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import jakarta.jms.Destination;
import jakarta.jms.JMSConsumer;
import jakarta.jms.Message;
import jakarta.jms.Queue;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Enumeration;
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

  @Test
  void sendEmitsExactlyOneProducerSpan() {
    Destination queue = queue();
    // a message that has not been sent has no destination yet, so the send argument is used
    Message message = message(null);

    testing.runWithSpan("parent", () -> new StubJmsProducer().send(queue, message));

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
  void receiveEmitsExactlyOneReceiveSpan(Function<JMSConsumer, Message> receiver) {
    JMSConsumer consumer = new StubJmsConsumer(message(queue()));

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
    JMSConsumer consumer = new StubJmsConsumer(null);

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

  private static Destination queue() {
    return (Destination)
        Proxy.newProxyInstance(
            Jms3SimplifiedApiTest.class.getClassLoader(),
            new Class<?>[] {TestQueue.class},
            (proxy, method, args) ->
                method.getName().equals("getQueueName")
                    ? QUEUE_NAME
                    : defaultValue(proxy, method, args));
  }

  private static Message message(Destination destination) {
    return (Message)
        Proxy.newProxyInstance(
            Jms3SimplifiedApiTest.class.getClassLoader(),
            new Class<?>[] {TestMessage.class},
            (proxy, method, args) -> {
              switch (method.getName()) {
                case "getJMSMessageID":
                  return MESSAGE_ID;
                case "getJMSDestination":
                  return destination;
                default:
                  return defaultValue(proxy, method, args);
              }
            });
  }

  // a proxy has to return a value that fits the method's return type: null for a primitive fails,
  // and the advice iterates the property names
  private static Object defaultValue(Object proxy, Method method, Object[] args) {
    if (method.getDeclaringClass() == Object.class) {
      switch (method.getName()) {
        case "equals":
          return proxy == args[0];
        case "hashCode":
          return System.identityHashCode(proxy);
        default:
          return "stub " + proxy.getClass().getInterfaces()[0].getSimpleName();
      }
    }
    Class<?> type = method.getReturnType();
    if (type == boolean.class) {
      return false;
    } else if (type == byte.class) {
      return (byte) 0;
    } else if (type == short.class) {
      return (short) 0;
    } else if (type == char.class) {
      return (char) 0;
    } else if (type == int.class) {
      return 0;
    } else if (type == long.class) {
      return 0L;
    } else if (type == float.class) {
      return 0f;
    } else if (type == double.class) {
      return 0d;
    } else if (type == Enumeration.class) {
      return emptyEnumeration();
    }
    return null;
  }

  // These interfaces are package-private so that the agent instruments the generated proxy classes.
  interface TestQueue extends Queue {}

  interface TestMessage extends Message {}
}
