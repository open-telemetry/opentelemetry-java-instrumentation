/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.test.utils.GcUtils;
import io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry.PulsarRequest;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.impl.SendCallback;
import org.apache.pulsar.client.impl.TopicMessageImpl;
import org.junit.jupiter.api.Test;

class VirtualFieldStoreTest {
  private static final ContextKey<String> TEST_KEY = ContextKey.named("test-key");

  @Test
  void storesProcessParent() {
    Message<?> message = mock(Message.class);
    Context processParent = Context.root().with(TEST_KEY, "parent");

    VirtualFieldStore.setProcessParentContext(message, processParent);

    assertThat(VirtualFieldStore.extractProcessParentContext(message)).isSameAs(processParent);
  }

  @Test
  void fallsBackToCurrentContext() {
    Message<?> message = mock(Message.class);
    Context processParent = Context.root().with(TEST_KEY, "parent");

    try (Scope ignored = processParent.makeCurrent()) {
      assertThat(VirtualFieldStore.extractProcessParentContext(message)).isSameAs(processParent);
    }
  }

  @Test
  void replacesProcessParent() {
    Message<?> message = mock(Message.class);
    Context firstParent = Context.root().with(TEST_KEY, "first");
    Context secondParent = Context.root().with(TEST_KEY, "second");

    VirtualFieldStore.setProcessParentContext(message, firstParent);
    VirtualFieldStore.setProcessParentContext(message, secondParent);

    assertThat(VirtualFieldStore.extractProcessParentContext(message)).isSameAs(secondParent);
  }

  @Test
  @SuppressWarnings("unchecked")
  void topicMessageWrapperPropagatesProcessParent() {
    Message<Object> message = mock(Message.class);
    TopicMessageImpl<Object> wrapper = mock(TopicMessageImpl.class);
    when(wrapper.getMessage()).thenReturn(message);
    Context processParent = Context.root().with(TEST_KEY, "parent");

    VirtualFieldStore.setProcessParentContext(wrapper, processParent);

    assertThat(VirtualFieldStore.extractProcessParentContext(message)).isSameAs(processParent);
    assertThat(VirtualFieldStore.extractProcessParentContext(wrapper)).isSameAs(processParent);
  }

  @Test
  void clearsProcessParentWhenMessageIsRecycled() {
    Message<?> message = mock(Message.class);
    VirtualFieldStore.setProcessParentContext(message, Context.root().with(TEST_KEY, "parent"));

    VirtualFieldStore.clearProcessParentContext(message);

    assertThat(VirtualFieldStore.extractProcessParentContext(message)).isSameAs(Context.current());
  }

  @Test
  void carriesProcessParentAcrossAsynchronousHandoff() {
    Message<?> message = mock(Message.class);
    Context processParent = Context.root().with(TEST_KEY, "parent");
    VirtualFieldStore.setProcessParentContext(message, processParent);

    CompletableFuture<Context> context =
        CompletableFuture.supplyAsync(() -> VirtualFieldStore.extractProcessParentContext(message));

    assertThat(context.join()).isSameAs(processParent);
  }

  @Test
  void sendCompletionTakesCapturedInvocationOnce() {
    SendCallback callback = mock(SendCallback.class);
    Context parent = Context.root().with(TEST_KEY, "parent");
    Context context = Context.root().with(TEST_KEY, "send");
    PulsarRequest request = mock(PulsarRequest.class);
    SendCallbackData data = SendCallbackData.create(parent, context, request);

    VirtualFieldStore.inject(callback, data);

    SendCallbackData callbackData = VirtualFieldStore.takeSendCallbackData(callback);
    assertThat(callbackData).isSameAs(data);
    assertThat(callbackData.context).isSameAs(context);
    assertThat(callbackData.request).isSameAs(request);
    assertThat(VirtualFieldStore.takeSendCallbackData(callback)).isNull();
  }

  @Test
  void clearsSendCallbackDataOnPulsarCompletionAfterFutureCancellation() {
    SendCallback callback = mock(SendCallback.class);
    Message<?> message = mock(Message.class);
    String topic = "persistent://public/default/send-callback-data";
    when(message.getTopicName()).thenReturn(topic);
    PulsarRequest request =
        PulsarRequest.create(message, ProducerData.create("pulsar://localhost:6650", topic));
    SendCallbackData data = SendCallbackData.create(Context.root(), Context.root(), request);
    CompletableFuture<MessageId> future = data.createSendFuture();
    when(callback.getFuture()).thenReturn(future);

    VirtualFieldStore.inject(callback, data);

    assertThat(VirtualFieldStore.extractSendCallbackData(message)).isSameAs(data);
    assertThat(future.cancel(false)).isTrue();
    assertThat(VirtualFieldStore.extractSendCallbackData(message)).isSameAs(data);
    assertThat(VirtualFieldStore.extractSendCallbackData(callback)).isSameAs(data);

    try (Scope ignored = data.callbackCompletionContext().makeCurrent()) {
      future.complete(mock(MessageId.class));
    }

    assertThat(future).isCancelled();
    assertThat(VirtualFieldStore.extractSendCallbackData(message)).isNull();
    assertThat(VirtualFieldStore.extractSendCallbackData(callback)).isNull();
  }

  @Test
  void prematureFutureCompletionDoesNotClearSendCallbackData() {
    SendCallback callback = mock(SendCallback.class);
    Message<?> message = mock(Message.class);
    String topic = "persistent://public/default/premature-send-future-completion";
    when(message.getTopicName()).thenReturn(topic);
    PulsarRequest request =
        PulsarRequest.create(message, ProducerData.create("pulsar://localhost:6650", topic));
    SendCallbackData data = SendCallbackData.create(Context.root(), Context.root(), request);
    CompletableFuture<MessageId> future = data.createSendFuture();
    VirtualFieldStore.inject(callback, data);

    future.complete(mock(MessageId.class));
    assertThat(VirtualFieldStore.extractSendCallbackData(message)).isSameAs(data);
    assertThat(VirtualFieldStore.extractSendCallbackData(message)).isSameAs(data);
    assertThat(VirtualFieldStore.extractSendCallbackData(callback)).isSameAs(data);
    try (Scope ignored = data.callbackCompletionContext().makeCurrent()) {
      future.complete(mock(MessageId.class));
    }
    assertThat(VirtualFieldStore.extractSendCallbackData(message)).isNull();
    assertThat(VirtualFieldStore.extractSendCallbackData(callback)).isNull();
  }

  @Test
  void messageAssociationDoesNotRetainAbandonedSendState()
      throws InterruptedException, TimeoutException {
    WeakReferences references = createAbandonedSend();

    GcUtils.awaitGc(references.callback, Duration.ofSeconds(10));
    expungeCollectedCallbacks();
    GcUtils.awaitGc(references.message, Duration.ofSeconds(10));
    GcUtils.awaitGc(references.data, Duration.ofSeconds(10));

    assertThat(references.message.get()).isNull();
    assertThat(references.data.get()).isNull();
  }

  @Test
  void messageAssociationReturnsNullAfterSendStateIsCollected()
      throws InterruptedException, TimeoutException {
    MessageAndWeakData association = createSendWithRetainedMessage();

    GcUtils.awaitGc(association.callback, Duration.ofSeconds(10));
    expungeCollectedCallbacks();
    GcUtils.awaitGc(association.data, Duration.ofSeconds(10));

    assertThat(VirtualFieldStore.extractSendCallbackData(association.message)).isNull();
  }

  private static WeakReferences createAbandonedSend() {
    MessageAndWeakData association = createSendWithRetainedMessage();
    return new WeakReferences(
        new WeakReference<>(association.message), association.data, association.callback);
  }

  private static void expungeCollectedCallbacks() {
    // Unit tests do not start the agent's background weak-reference cleaner.
    VirtualFieldStore.extractSendCallbackData(mock(SendCallback.class));
  }

  private static MessageAndWeakData createSendWithRetainedMessage() {
    Message<?> message = mock(Message.class);
    String topic = "persistent://public/default/abandoned-send";
    when(message.getTopicName()).thenReturn(topic);
    SendCallback callback = mock(SendCallback.class);
    PulsarRequest request =
        PulsarRequest.create(message, ProducerData.create("pulsar://localhost:6650", topic));
    SendCallbackData data = SendCallbackData.create(Context.root(), Context.root(), request);
    VirtualFieldStore.inject(callback, data);
    return new MessageAndWeakData(
        message, new WeakReference<>(data), new WeakReference<>(callback));
  }

  private static final class MessageAndWeakData {
    private final Message<?> message;
    private final WeakReference<SendCallbackData> data;
    private final WeakReference<SendCallback> callback;

    private MessageAndWeakData(
        Message<?> message,
        WeakReference<SendCallbackData> data,
        WeakReference<SendCallback> callback) {
      this.message = message;
      this.data = data;
      this.callback = callback;
    }
  }

  private static final class WeakReferences {
    private final WeakReference<Message<?>> message;
    private final WeakReference<SendCallbackData> data;
    private final WeakReference<SendCallback> callback;

    private WeakReferences(
        WeakReference<Message<?>> message,
        WeakReference<SendCallbackData> data,
        WeakReference<SendCallback> callback) {
      this.message = message;
      this.data = data;
      this.callback = callback;
    }
  }
}
