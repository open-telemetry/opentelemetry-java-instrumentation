/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v6_0;

import static io.opentelemetry.instrumentation.api.internal.SpanKey.CONSUMER_PROCESS;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.util.Collections.emptyEnumeration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.jms.listener.SessionAwareMessageListener;

class SpringJmsMessageListenerTest {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-jms-6.0";

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void sameMessageDelegationProducesOneProcessOperation() throws Exception {
    Message message = message("same");
    SessionAwareMessageListener<Message> inner = new TestMessageListener(ignored -> {});
    SessionAwareMessageListener<Message> outer =
        new TestMessageListener(innerMessage -> inner.onMessage(innerMessage, null));

    outer.onMessage(message, null);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(span -> span.hasName("process same").hasNoParent()));
    assertProcessDuration("same", 1);
  }

  @Test
  void nestedMessagesHonorProcessSpanSuppression() throws Exception {
    assertThat(Boolean.getBoolean("otel.javaagent.testing.fail-on-context-leak")).isTrue();
    Message outerMessage = message("nested");
    Message innerMessage = message("nested");
    SpanContext callerSpan = Span.current().getSpanContext();
    AtomicReference<SpanContext> outerSpan = new AtomicReference<>();
    SessionAwareMessageListener<Message> inner =
        new TestMessageListener(
            ignored -> {
              assertThat(Span.current().getSpanContext()).isEqualTo(outerSpan.get());
            });
    SessionAwareMessageListener<Message> outer =
        new TestMessageListener(
            ignored -> {
              outerSpan.set(Span.current().getSpanContext());
              assertThat(outerSpan.get().isValid()).isTrue();
              assertThat(CONSUMER_PROCESS.fromContextOrNull(Context.current())).isNotNull();
              inner.onMessage(innerMessage, null);
              assertThat(Span.current().getSpanContext()).isEqualTo(outerSpan.get());
            });

    outer.onMessage(outerMessage, null);

    assertThat(Span.current().getSpanContext()).isEqualTo(callerSpan);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(span -> span.hasName("process nested").hasNoParent()));
    assertProcessDuration("nested", 1);
  }

  @Test
  void unrelatedMessagesUnderSameParentProduceIndependentProcessOperations() throws Exception {
    Message first = message("independent");
    Message second = message("independent");
    SessionAwareMessageListener<Message> listener = new TestMessageListener(ignored -> {});

    testing.runWithSpan(
        "caller",
        () -> {
          listener.onMessage(first, null);
          listener.onMessage(second, null);
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("caller").hasNoParent(),
                span -> span.hasName("process independent").hasParent(trace.getSpan(0)),
                span -> span.hasName("process independent").hasParent(trace.getSpan(0))));
    assertProcessDuration("independent", 2);
  }

  private static void assertProcessDuration(String destination, long count) {
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "messaging.process.duration",
        metrics ->
            metrics
                .singleElement()
                .satisfies(
                    metric ->
                        assertThat(metric.getHistogramData().getPoints())
                            .singleElement()
                            .satisfies(
                                point -> {
                                  assertThat(point.getCount()).isEqualTo(count);
                                  assertThat(point.getAttributes())
                                      .isEqualTo(metricAttributes(destination));
                                })));
  }

  private static Attributes metricAttributes(String destination) {
    return Attributes.of(
        MESSAGING_OPERATION_NAME,
        "process",
        MESSAGING_SYSTEM,
        "jms",
        MESSAGING_DESTINATION_NAME,
        destination);
  }

  private static Message message(String destinationName) throws Exception {
    Queue destination = mock(Queue.class);
    when(destination.getQueueName()).thenReturn(destinationName);
    Message message = mock(Message.class);
    when(message.getJMSDestination()).thenReturn(destination);
    when(message.getPropertyNames()).thenReturn(emptyEnumeration());
    return message;
  }

  private static final class TestMessageListener implements SessionAwareMessageListener<Message> {
    private final ListenerAction delegate;

    private TestMessageListener(ListenerAction delegate) {
      this.delegate = delegate;
    }

    @Override
    public void onMessage(Message message, Session session) throws JMSException {
      delegate.accept(message);
    }
  }

  @FunctionalInterface
  private interface ListenerAction {
    void accept(Message message) throws JMSException;
  }
}
