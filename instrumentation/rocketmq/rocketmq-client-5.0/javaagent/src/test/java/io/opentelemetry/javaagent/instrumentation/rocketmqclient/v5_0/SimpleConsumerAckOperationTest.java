/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.javaagent.testing.common.TestAgentListenerAccess.getAndResetAdviceFailureCount;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import org.apache.rocketmq.client.apis.ClientException;
import org.apache.rocketmq.client.apis.consumer.SimpleConsumer;
import org.apache.rocketmq.client.apis.message.MessageId;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@SuppressWarnings("deprecation") // using deprecated semconv
class SimpleConsumerAckOperationTest {

  private static final String TOPIC = "settle-topic";
  private static final String CONSUMER_GROUP = "settle-consumer-group";
  private static final String MESSAGE_ID = "settle-message-id";

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private SimpleConsumer consumer;
  private MessageView message;

  @BeforeEach
  void setUp() {
    consumer = mock(SimpleConsumer.class);
    when(consumer.getConsumerGroup()).thenReturn(CONSUMER_GROUP);
    message = mock(MessageView.class);
    when(message.getTopic()).thenReturn(TOPIC);
    MessageId messageId = mock(MessageId.class);
    when(messageId.toString()).thenReturn(MESSAGE_ID);
    when(message.getMessageId()).thenReturn(messageId);
  }

  @Test
  void shouldHonorStableMessagingGating() {
    testing.runWithSpan(
        "parent",
        () -> {
          SimpleConsumerAckOperation operation =
              SimpleConsumerAckOperation.start(consumer, message);
          if (emitStableMessagingSemconv()) {
            assertThat(operation).isNotNull();
            operation.end(null);
          } else {
            assertThat(operation).isNull();
          }
        });

    testing.waitAndAssertTraces(
        trace -> {
          if (emitStableMessagingSemconv()) {
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    assertAckSpan(span, null).hasName("ack " + TOPIC).hasParent(trace.getSpan(0)));
          } else {
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent());
          }
        });
  }

  @Test
  void shouldEndSynchronousError() {
    assumeTrue(emitStableMessagingSemconv());
    ClientException error = new ClientException("ack failed");

    testing.runWithSpan(
        "parent", () -> SimpleConsumerAckOperation.start(consumer, message).end(error));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    assertAckSpan(span, ClientException.class)
                        .hasStatus(StatusData.error())
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  void shouldRestoreCallDepthWhenSpanStartFails() {
    assumeTrue(emitStableMessagingSemconv());
    MessageView invalidMessage = mock(MessageView.class);
    when(invalidMessage.getTopic()).thenThrow(new IllegalStateException("invalid message"));

    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(SimpleConsumerAckOperation.start(consumer, invalidMessage)).isNull();
          assertThat(getAndResetAdviceFailureCount()).isEqualTo(1);
          SimpleConsumerAckOperation operation =
              SimpleConsumerAckOperation.start(consumer, message);
          assertThat(operation).isNotNull();
          operation.end(null);
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span -> assertAckSpan(span, null).hasParent(trace.getSpan(0))));
  }

  @Test
  void shouldEndAsynchronousSuccessOnCompletion() {
    assumeTrue(emitStableMessagingSemconv());
    CompletableFuture<Void> future = new CompletableFuture<>();

    testing.runWithSpan(
        "parent", () -> SimpleConsumerAckOperation.start(consumer, message).endAsync(future, null));

    assertThat(testing.spans()).hasSize(1);
    future.complete(null);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span -> assertAckSpan(span, null).hasParent(trace.getSpan(0))));
  }

  @Test
  void shouldEndAsynchronousErrorOnCompletion() {
    assumeTrue(emitStableMessagingSemconv());
    CompletableFuture<Void> future = new CompletableFuture<>();
    ClientException error = new ClientException("ack failed");

    testing.runWithSpan(
        "parent", () -> SimpleConsumerAckOperation.start(consumer, message).endAsync(future, null));

    assertThat(testing.spans()).hasSize(1);
    future.completeExceptionally(error);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    assertAckSpan(span, ClientException.class)
                        .hasStatus(StatusData.error())
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  void shouldEndAsynchronousCancellationOnCompletion() {
    assumeTrue(emitStableMessagingSemconv());
    CompletableFuture<Void> future = new CompletableFuture<>();

    testing.runWithSpan(
        "parent", () -> SimpleConsumerAckOperation.start(consumer, message).endAsync(future, null));

    assertThat(testing.spans()).hasSize(1);
    assertThat(future.cancel(false)).isTrue();
    assertThat(future).isCancelled();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    assertAckSpan(span, CancellationException.class)
                        .hasStatus(StatusData.error())
                        .hasParent(trace.getSpan(0))));
  }

  private static SpanDataAssert assertAckSpan(
      SpanDataAssert span, Class<? extends Throwable> errorType) {
    return span.hasName("ack " + TOPIC)
        .hasKind(SpanKind.CLIENT)
        .hasAttributesSatisfyingExactly(
            equalTo(MESSAGING_CONSUMER_GROUP_NAME, CONSUMER_GROUP),
            equalTo(MESSAGING_DESTINATION_NAME, TOPIC),
            equalTo(MESSAGING_MESSAGE_ID, MESSAGE_ID),
            equalTo(MESSAGING_OPERATION_NAME, "ack"),
            equalTo(MESSAGING_OPERATION_TYPE, "settle"),
            equalTo(MESSAGING_SYSTEM, "rocketmq"),
            equalTo(ERROR_TYPE, errorType == null ? null : errorType.getName()));
  }
}
