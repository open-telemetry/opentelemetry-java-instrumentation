/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v1_1;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class Jms1ProducerCompatibilityTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @ParameterizedTest
  @CsvSource({"0,false", "1,false", "2,false", "3,false", "0,true", "1,true", "2,true", "3,true"})
  void synchronousSendWithoutCompletionListener(int overload, boolean failed) throws Exception {
    assertThatThrownBy(() -> Class.forName("javax.jms.CompletionListener"))
        .isInstanceOf(ClassNotFoundException.class);
    TestProducer producer = new TestProducer();
    if (failed) {
      producer.failure = new JMSException("send failed");
    }
    if (failed) {
      assertThatThrownBy(() -> send(producer, overload)).isSameAs(producer.failure);
    } else {
      send(producer, overload);
    }
    assertThat(Span.current().getSpanContext().isValid()).isFalse();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("send queue")
                        .hasKind(SpanKind.PRODUCER)
                        .hasNoParent()
                        .hasStatusSatisfying(
                            status -> status.hasCode(failed ? StatusCode.ERROR : StatusCode.UNSET))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            equalTo(MESSAGING_OPERATION_NAME, "send"),
                            equalTo(MESSAGING_OPERATION_TYPE, "send"),
                            equalTo(MESSAGING_DESTINATION_NAME, "queue"),
                            equalTo(ERROR_TYPE, failed ? JMSException.class.getName() : null))));
  }

  private static void send(TestProducer producer, int overload) throws JMSException {
    Message message = mock(Message.class);
    switch (overload) {
      case 0:
        producer.send(message);
        break;
      case 1:
        producer.send(message, 2, 4, 0);
        break;
      case 2:
        producer.send(producer.destination, message);
        break;
      default:
        producer.send(producer.destination, message, 2, 4, 0);
    }
  }

  public static class TestProducer implements MessageProducer {
    final Queue destination = mock(Queue.class);
    JMSException failure;

    TestProducer() throws JMSException {
      when(destination.getQueueName()).thenReturn("queue");
    }

    @Override
    public void send(Message message) throws JMSException {
      assertThat(Span.current().getSpanContext().isValid()).isTrue();
      if (failure != null) {
        throw failure;
      }
    }

    @Override
    public void send(Message message, int mode, int priority, long ttl) throws JMSException {
      send(message);
    }

    @Override
    public void send(Destination destination, Message message) throws JMSException {
      send(message);
    }

    @Override
    public void send(Destination destination, Message message, int mode, int priority, long ttl)
        throws JMSException {
      send(message);
    }

    @Override
    public Destination getDestination() {
      return destination;
    }

    @Override
    public void close() {}

    @Override
    public void setDisableMessageID(boolean value) {}

    @Override
    public boolean getDisableMessageID() {
      return false;
    }

    @Override
    public void setDisableMessageTimestamp(boolean value) {}

    @Override
    public boolean getDisableMessageTimestamp() {
      return false;
    }

    @Override
    public void setDeliveryMode(int value) {}

    @Override
    public int getDeliveryMode() {
      return 2;
    }

    @Override
    public void setPriority(int value) {}

    @Override
    public int getPriority() {
      return 4;
    }

    @Override
    public void setTimeToLive(long value) {}

    @Override
    public long getTimeToLive() {
      return 0;
    }
  }
}
