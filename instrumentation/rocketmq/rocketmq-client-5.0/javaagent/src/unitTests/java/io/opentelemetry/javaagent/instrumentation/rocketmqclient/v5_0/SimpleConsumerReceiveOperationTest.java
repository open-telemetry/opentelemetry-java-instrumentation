/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static net.javacrumbs.futureconverter.java8guava.FutureConverter.toCompletableFuture;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.apache.rocketmq.client.apis.consumer.SimpleConsumer;
import org.apache.rocketmq.client.apis.message.MessageId;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.java.message.MessageViewImpl;
import org.apache.rocketmq.client.java.message.protocol.Resource;
import org.apache.rocketmq.client.java.route.MessageQueueImpl;
import org.apache.rocketmq.shaded.com.google.common.util.concurrent.AbstractFuture;
import org.apache.rocketmq.shaded.com.google.common.util.concurrent.ListenableFuture;
import org.apache.rocketmq.shaded.com.google.common.util.concurrent.MoreExecutors;
import org.apache.rocketmq.shaded.com.google.common.util.concurrent.SettableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("deprecation") // using deprecated semconv
class SimpleConsumerReceiveOperationTest {

  @RegisterExtension
  static final LibraryInstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void recordsReceiveWhenSuccessRacesWithCancellation()
      throws ExecutionException, InterruptedException, TimeoutException {
    List<MessageView> messages = singletonList(message());
    SettableFuture<List<MessageView>> original = SettableFuture.create();
    CountDownLatch successPublished = new CountDownLatch(1);
    CountDownLatch resumeCompletion = new CountDownLatch(1);
    // Pause after the original result is visible, before its telemetry listener runs.
    original.addListener(
        () -> {
          successPublished.countDown();
          await(resumeCompletion);
        },
        MoreExecutors.directExecutor());
    ListenableFuture<List<MessageView>> wrapped = operation().wrap(original);
    CompletableFuture<List<MessageView>> returned = toCompletableFuture(wrapped);
    CompletableFuture<Boolean> completion =
        CompletableFuture.supplyAsync(() -> original.set(messages));

    try {
      assertThat(successPublished.await(10, SECONDS)).isTrue();
      assertThat(original.get(10, SECONDS)).isSameAs(messages);
      assertThat(returned.isDone()).isFalse();
      assertThat(returned.cancel(false)).isTrue();
      assertThat(wrapped.isCancelled()).isTrue();
    } finally {
      resumeCompletion.countDown();
      assertThat(completion.get(10, SECONDS)).isTrue();
    }

    assertThat(original.isCancelled()).isFalse();
    assertThat(returned.isCancelled()).isTrue();
    assertSuccessfulReceive();
  }

  @Test
  void publishesSuccessAfterTelemetry()
      throws ExecutionException, InterruptedException, TimeoutException {
    List<MessageView> messages = singletonList(message());
    SettableFuture<List<MessageView>> original = SettableFuture.create();
    CompletableFuture<List<MessageView>> returned = toCompletableFuture(operation().wrap(original));
    CompletableFuture<List<MessageView>> observed =
        returned.thenApply(
            result -> {
              assertThat(testing.spans()).hasSize(1);
              return result;
            });
    original.set(messages);

    assertThat(observed.get(10, SECONDS)).isSameAs(messages);
    assertSuccessfulReceive();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @SuppressWarnings("Interruption") // verify that the caller's interrupt flag reaches the source
  void propagatesCancellation(boolean mayInterruptIfRunning) {
    CancellationTrackingFuture original = new CancellationTrackingFuture();
    ListenableFuture<List<MessageView>> wrapped = operation().wrap(original);
    CompletableFuture<List<MessageView>> returned = toCompletableFuture(wrapped);

    assertThat(returned.cancel(mayInterruptIfRunning)).isTrue();
    assertThat(original.isCancelled()).isTrue();
    assertThat(original.interrupted()).isEqualTo(mayInterruptIfRunning);
    assertThat(wrapped.isCancelled()).isTrue();
    assertThat(returned.isCancelled()).isTrue();
    assertCancelledReceive();
  }

  @Test
  void preservesOriginalCancellation() {
    SettableFuture<List<MessageView>> original = SettableFuture.create();
    ListenableFuture<List<MessageView>> wrapped = operation().wrap(original);

    assertThat(original.cancel(false)).isTrue();
    assertThat(wrapped.isCancelled()).isTrue();
    assertThatThrownBy(() -> wrapped.get(10, SECONDS)).isInstanceOf(CancellationException.class);
    assertCancelledReceive();
  }

  private static SimpleConsumerReceiveOperation operation() {
    SimpleConsumer consumer = mock(SimpleConsumer.class);
    when(consumer.getConsumerGroup()).thenReturn("consumer-group");
    return SimpleConsumerReceiveOperation.start(consumer);
  }

  private static MessageView message() {
    MessageId messageId = mock(MessageId.class);
    when(messageId.toString()).thenReturn("message-id");
    MessageQueueImpl queue = mock(MessageQueueImpl.class);
    when(queue.getTopicResource()).thenReturn(new Resource("topic"));
    MessageViewImpl message = mock(MessageViewImpl.class);
    when(message.getMessageQueue()).thenReturn(queue);
    when(message.getMessageId()).thenReturn(messageId);
    when(message.getTopic()).thenReturn("topic");
    when(message.getProperties()).thenReturn(emptyMap());
    when(message.getKeys()).thenReturn(singletonList("key"));
    when(message.getTag()).thenReturn(Optional.of("tag"));
    when(message.getMessageGroup()).thenReturn(Optional.empty());
    when(message.getDeliveryTimestamp()).thenReturn(Optional.empty());
    return message;
  }

  private static void await(CountDownLatch latch) {
    try {
      assertThat(latch.await(10, SECONDS)).isTrue();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }

  private static void assertSuccessfulReceive() {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("receive topic")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasStatus(StatusData.unset())
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_CONSUMER_GROUP_NAME, "consumer-group"),
                            equalTo(stringKey("messaging.system.name"), "rocketmq"),
                            equalTo(MESSAGING_DESTINATION_NAME, "topic"),
                            equalTo(MESSAGING_OPERATION_NAME, "receive"),
                            equalTo(MESSAGING_OPERATION_TYPE, "receive"),
                            equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 1),
                            equalTo(stringKey("rocketmq.message.tag"), "tag"),
                            equalTo(
                                stringArrayKey("rocketmq.message.keys"), singletonList("key")))));
  }

  private static void assertCancelledReceive() {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("receive")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_CONSUMER_GROUP_NAME, "consumer-group"),
                            equalTo(stringKey("messaging.system.name"), "rocketmq"),
                            equalTo(MESSAGING_OPERATION_NAME, "receive"),
                            equalTo(MESSAGING_OPERATION_TYPE, "receive"),
                            equalTo(ERROR_TYPE, CancellationException.class.getName()))));
  }

  private static final class CancellationTrackingFuture extends AbstractFuture<List<MessageView>> {
    boolean interrupted() {
      return wasInterrupted();
    }
  }
}
