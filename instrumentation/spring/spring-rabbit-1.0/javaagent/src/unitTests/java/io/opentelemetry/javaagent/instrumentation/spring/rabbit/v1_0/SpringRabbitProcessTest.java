/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import io.opentelemetry.api.impl.InstrumentationUtil;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import java.net.InetAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;

class SpringRabbitProcessTest {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-rabbit-1.0";
  private static final VirtualField<Message, Context> PROCESSING_CONTEXT =
      VirtualField.find(Message.class, Context.class);

  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private final Channel channel = mock(Channel.class);
  private final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();

  @BeforeEach
  void setUp() {
    Connection connection = mock(Connection.class);
    when(channel.getConnection()).thenReturn(connection);
    when(connection.getAddress()).thenReturn(InetAddress.getLoopbackAddress());
    when(connection.getPort()).thenReturn(5672);
    container.setMessageListener((MessageListener) message -> {});
  }

  @Test
  void eachProcessingAttemptRecordsOrdinaryMetrics() {
    Message message = message();
    testing.runWithSpan(
        "parent",
        () -> {
          assertThatThrownBy(
                  () ->
                      process(
                          message,
                          () -> {
                            throw new IllegalStateException("failed attempt");
                          }))
              .isInstanceOf(IllegalStateException.class);
          process(message, () -> {});
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent"),
                span -> span.hasException(new IllegalStateException("failed attempt")),
                span -> span.hasParent(trace.getSpan(0))));
    assertConsumedMessages(2);
    assertProcessAttempts(2);
    assertThat(Context.current()).isSameAs(Context.root());
  }

  @Test
  void batchRecordsAllMessagesAndLinks() {
    Message first = message();
    Message second = message();
    SpanContext firstCreation = injectCreation(first, "first");
    SpanContext secondCreation = injectCreation(second, "second");
    testing.runWithSpan("parent", () -> process(asList(first, second), () -> {}));

    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("first")),
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("second")),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent"),
                span ->
                    span.hasAttribute(equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 2))
                        .satisfies(
                            data ->
                                assertThat(data.getLinks())
                                    .extracting(link -> link.getSpanContext().getSpanId())
                                    .containsExactlyElementsOf(
                                        emitStableMessagingSemconv()
                                            ? asList(
                                                firstCreation.getSpanId(),
                                                secondCreation.getSpanId())
                                            : emptyList()))));
    assertConsumedMessages(2);
    assertProcessAttempts(1);
  }

  @Test
  void batchWithSharedCreationContextLinksEachMessage() {
    Message first = message();
    Message second = message();
    SpanContext creation = injectCreation(first, "creation");
    second.getMessageProperties().getHeaders().putAll(first.getMessageProperties().getHeaders());

    testing.runWithSpan("parent", () -> process(asList(first, second), () -> {}));

    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("creation")),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent"),
                span ->
                    span.hasAttribute(equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 2))
                        .satisfies(
                            data ->
                                assertThat(data.getLinks())
                                    .extracting(link -> link.getSpanContext().getSpanId())
                                    .containsExactlyElementsOf(
                                        emitStableMessagingSemconv()
                                            ? asList(creation.getSpanId(), creation.getSpanId())
                                            : emptyList()))));
  }

  @Test
  void independentNestedMessageUsesItsOwnProcessContext() {
    Message outer = message();
    Message inner = message();
    process(
        outer,
        () -> {
          SpanContext outerSpan = Span.current().getSpanContext();
          process(inner, () -> assertThat(Span.current().getSpanContext()).isNotEqualTo(outerSpan));
          assertThat(Span.current().getSpanContext()).isEqualTo(outerSpan);
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasNoParent(), span -> span.hasParent(trace.getSpan(0))));
  }

  @Test
  void processingContextIsScopedToRequestMessages() {
    Message first = message();
    Message second = message();
    Message unrelated = message();

    process(
        asList(first, second),
        () -> {
          assertThat(PROCESSING_CONTEXT.get(first)).isSameAs(Context.current());
          assertThat(PROCESSING_CONTEXT.get(second)).isSameAs(Context.current());
          assertThat(PROCESSING_CONTEXT.get(unrelated)).isNull();
        });

    assertThat(PROCESSING_CONTEXT.get(first)).isNull();
    assertThat(PROCESSING_CONTEXT.get(second)).isNull();
    assertThat(PROCESSING_CONTEXT.get(unrelated)).isNull();
  }

  @Test
  void nestedSameMessageRestoresOuterProcessingContextAfterFailure() {
    Message message = message();

    process(
        message,
        () -> {
          Context outerContext = Context.current();
          assertThat(PROCESSING_CONTEXT.get(message)).isSameAs(outerContext);

          assertThatThrownBy(
                  () ->
                      process(
                          message,
                          () -> {
                            assertThat(PROCESSING_CONTEXT.get(message))
                                .isSameAs(Context.current())
                                .isNotSameAs(outerContext);
                            throw new IllegalStateException("nested failure");
                          }))
              .isInstanceOf(IllegalStateException.class);

          assertThat(PROCESSING_CONTEXT.get(message)).isSameAs(outerContext);
        });

    assertThat(PROCESSING_CONTEXT.get(message)).isNull();
  }

  @Test
  void skippedProcessingDoesNotInstallContext() {
    Message message = message();
    assertThat(
            AbstractMessageListenerContainerInstrumentation.ExecuteListenerAdvice.onEnter(
                new SimpleMessageListenerContainer(), channel, message))
        .isNull();
    assertThat(PROCESSING_CONTEXT.get(message)).isNull();
  }

  @Test
  void explicitSuppressionDoesNotInstallContext() {
    Message message = message();
    InstrumentationUtil.suppressInstrumentation(
        () ->
            assertThat(
                    AbstractMessageListenerContainerInstrumentation.ExecuteListenerAdvice.onEnter(
                        container, channel, message))
                .isNull());
    assertThat(PROCESSING_CONTEXT.get(message)).isNull();
    process(message, () -> {});
    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasNoParent()));
  }

  private void process(Object message, Runnable callback) {
    AbstractMessageListenerContainerInstrumentation.ExecuteListenerAdvice.AdviceScope scope =
        AbstractMessageListenerContainerInstrumentation.ExecuteListenerAdvice.onEnter(
            container, channel, message);
    assertThat(scope).isNotNull();
    Throwable error = null;
    try {
      callback.run();
    } catch (Throwable t) {
      error = t;
      throw t;
    } finally {
      AbstractMessageListenerContainerInstrumentation.ExecuteListenerAdvice.onExit(error, scope);
    }
  }

  private static Message message() {
    MessageProperties properties = new MessageProperties();
    properties.setReceivedExchange("");
    properties.setReceivedRoutingKey("queue");
    return new Message(new byte[0], properties);
  }

  private static SpanContext injectCreation(Message message, String name) {
    return testing.runWithSpan(
        name,
        () -> {
          testing
              .getOpenTelemetry()
              .getPropagators()
              .getTextMapPropagator()
              .inject(
                  Context.current(),
                  message,
                  (carrier, key, value) -> carrier.getMessageProperties().setHeader(key, value));
          return Span.current().getSpanContext();
        });
  }

  private static void assertConsumedMessages(long count) {
    if (!emitStableMessagingSemconv()) {
      assertThat(testing.metrics()).isEmpty();
      return;
    }
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "messaging.client.consumed.messages",
        metrics ->
            metrics.satisfiesExactly(
                metric ->
                    assertThat(
                            metric.getLongSumData().getPoints().stream()
                                .mapToLong(LongPointData::getValue)
                                .sum())
                        .isEqualTo(count)));
  }

  private static void assertProcessAttempts(long count) {
    if (!emitStableMessagingSemconv()) {
      return;
    }
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "messaging.process.duration",
        metrics ->
            metrics.satisfiesExactly(
                metric ->
                    assertThat(
                            metric.getHistogramData().getPoints().stream()
                                .mapToLong(point -> point.getCount())
                                .sum())
                        .isEqualTo(count)));
  }
}
