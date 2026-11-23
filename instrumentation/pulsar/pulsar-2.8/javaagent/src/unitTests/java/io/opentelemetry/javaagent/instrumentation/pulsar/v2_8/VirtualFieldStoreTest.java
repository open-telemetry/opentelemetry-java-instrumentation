/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void messageAssociationStoresOnlyCallerContext(boolean withSpan) {
    Message<?> message = mock(Message.class);
    SendCallback callback = mock(SendCallback.class);
    Context caller = Context.root().with(TEST_KEY, "caller");
    if (withSpan) {
      caller =
          caller.with(
              Span.wrap(
                  SpanContext.create(
                      "00000000000000000000000000000001",
                      "0000000000000001",
                      TraceFlags.getSampled(),
                      TraceState.getDefault())));
    }
    caller = Baggage.builder().put("pulsar-test", "caller").build().storeInContext(caller);
    Context processParent = Context.root().with(TEST_KEY, "consumer");
    String topic = "persistent://public/default/caller-context";
    when(message.getTopicName()).thenReturn(topic);
    PulsarRequest request =
        PulsarRequest.create(message, ProducerData.create("pulsar://localhost:6650", topic));
    SendCallbackData data = SendCallbackData.create(caller, Context.root(), request);
    VirtualFieldStore.setProcessParentContext(message, processParent);

    VirtualFieldStore.inject(callback, data);

    Context parent = VirtualFieldStore.extractSendCallerContext(message);
    assertThat(parent).isSameAs(caller);
    try (Scope ignored = parent.makeCurrent()) {
      assertThat(SendCallbackData.current()).isNull();
      assertThat(Context.current().get(TEST_KEY)).isEqualTo("caller");
      assertThat(Baggage.current().getEntryValue("pulsar-test")).isEqualTo("caller");
      assertThat(Span.current().getSpanContext().isValid()).isEqualTo(withSpan);
    }
    data.end(null);
    assertThat(VirtualFieldStore.extractSendCallerContext(message)).isNull();
    assertThat(VirtualFieldStore.extractProcessParentContext(message)).isSameAs(processParent);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void oldOperationCleanupPreservesNewAssociations(boolean takeBeforeReplacement) {
    SendCallback callback = mock(SendCallback.class);
    Message<?> message = mock(Message.class);
    String topic = "persistent://public/default/reused-send-associations";
    when(message.getTopicName()).thenReturn(topic);
    PulsarRequest request =
        PulsarRequest.create(message, ProducerData.create("pulsar://localhost:6650", topic));
    SendCallbackData first = SendCallbackData.create(Context.root(), Context.root(), request);
    Context caller = Context.root().with(TEST_KEY, "second");
    SendCallbackData second = SendCallbackData.create(caller, Context.root(), request);
    VirtualFieldStore.inject(callback, first);
    if (takeBeforeReplacement) {
      assertThat(VirtualFieldStore.takeSendCallbackData(callback)).isSameAs(first);
      assertThat(VirtualFieldStore.takeSendCallbackData(callback)).isNull();
    }
    VirtualFieldStore.inject(callback, second);

    first.end(null);

    assertThat(first.callbackAssociation().get()).isNull();
    assertThat(VirtualFieldStore.extractSendCallbackData(callback)).isSameAs(second);
    assertThat(VirtualFieldStore.extractSendCallerContext(message)).isSameAs(caller);
    second.end(null);
    assertThat(VirtualFieldStore.takeSendCallbackData(callback)).isNull();
    assertThat(VirtualFieldStore.extractSendCallerContext(message)).isNull();
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
    SendCallbackData.CallbackAssociation association = data.callbackAssociation();

    assertThat(VirtualFieldStore.extractSendCallerContext(message)).isSameAs(Context.root());
    assertThat(future.cancel(false)).isTrue();
    assertThat(VirtualFieldStore.extractSendCallerContext(message)).isSameAs(Context.root());
    assertThat(VirtualFieldStore.extractSendCallbackData(callback)).isSameAs(data);
    assertThat(association.get()).isSameAs(data);

    try (Scope ignored = data.callbackCompletionContext().makeCurrent()) {
      future.complete(mock(MessageId.class));
    }

    assertThat(future).isCancelled();
    assertThat(association.get()).isNull();
    assertThat(VirtualFieldStore.extractSendCallerContext(message)).isNull();
    assertThat(VirtualFieldStore.extractSendCallbackData(callback)).isNull();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void prematureFutureCompletionPreservesApplicationContextAndSendCallbackData(boolean fail) {
    SendCallback callback = mock(SendCallback.class);
    Message<?> message = mock(Message.class);
    String topic = "persistent://public/default/premature-send-future-completion";
    when(message.getTopicName()).thenReturn(topic);
    PulsarRequest request =
        PulsarRequest.create(message, ProducerData.create("pulsar://localhost:6650", topic));
    Context caller =
        Baggage.builder()
            .put("pulsar-test", "caller")
            .build()
            .storeInContext(
                Context.root()
                    .with(
                        Span.wrap(
                            SpanContext.create(
                                "00000000000000000000000000000001",
                                "0000000000000001",
                                TraceFlags.getSampled(),
                                TraceState.getDefault())))
                    .with(TEST_KEY, "caller"));
    Context application =
        Baggage.builder()
            .put("pulsar-test", "application")
            .build()
            .storeInContext(
                Context.root()
                    .with(
                        Span.wrap(
                            SpanContext.create(
                                "00000000000000000000000000000002",
                                "0000000000000002",
                                TraceFlags.getSampled(),
                                TraceState.getDefault())))
                    .with(TEST_KEY, "application"));
    SendCallbackData data = SendCallbackData.create(caller, caller, request);
    CompletableFuture<MessageId> future = data.createSendFuture();
    VirtualFieldStore.inject(callback, data);
    MessageId messageId = mock(MessageId.class);
    IllegalStateException failure = new IllegalStateException("application failure");
    CompletableFuture<Context> continuation =
        future.handle(
            (result, error) -> {
              assertThat(result).isSameAs(fail ? null : messageId);
              assertThat(error).isSameAs(fail ? failure : null);
              return Context.current();
            });

    assertThat(future).isNotDone();
    Context previous = Context.current();
    try (Scope ignored = application.makeCurrent()) {
      if (fail) {
        assertThat(future.completeExceptionally(failure)).isTrue();
      } else {
        assertThat(future.complete(messageId)).isTrue();
      }
      assertThat(Context.current()).isSameAs(application);
    }
    assertThat(continuation.join()).isSameAs(application);
    assertThat(Context.current()).isSameAs(previous);
    assertThat(VirtualFieldStore.extractSendCallerContext(message)).isSameAs(caller);
    assertThat(VirtualFieldStore.extractSendCallbackData(callback)).isSameAs(data);
    try (Scope ignored = data.callbackCompletionContext().makeCurrent()) {
      Context completionContext = Context.current();
      assertThat(future.complete(mock(MessageId.class))).isFalse();
      assertThat(Context.current()).isSameAs(completionContext);
    }
    assertThat(Context.current()).isSameAs(previous);
    assertThat(VirtualFieldStore.extractSendCallerContext(message)).isNull();
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
  void messageAssociationRetainsCallerContextWithoutRetainingSendState()
      throws InterruptedException, TimeoutException {
    MessageAndWeakData association = createSendWithRetainedMessage();

    GcUtils.awaitGc(association.callback, Duration.ofSeconds(10));
    expungeCollectedCallbacks();
    GcUtils.awaitGc(association.data, Duration.ofSeconds(10));

    assertThat(VirtualFieldStore.extractSendCallerContext(association.message))
        .isSameAs(Context.root());
  }

  @Test
  void liveCallbackRetainsSendStateUntilTerminalCompletion()
      throws InterruptedException, TimeoutException {
    SendCallback callback = mock(SendCallback.class);
    WeakReferences references = createSendWithRetainedCallback(callback);

    GcUtils.awaitGc(Duration.ofSeconds(10));

    assertThat(references.data.get()).isNotNull();
    assertThat(VirtualFieldStore.extractSendCallbackData(callback)).isSameAs(references.data.get());
    completeSend(callback);
    assertThat(VirtualFieldStore.extractSendCallbackData(callback)).isNull();
    GcUtils.awaitGc(references.data, Duration.ofSeconds(10));
    assertThat(VirtualFieldStore.takeSendCallbackData(callback)).isNull();
  }

  private static void completeSend(SendCallback callback) {
    // Linked completions end the operation without taking the callback's association.
    VirtualFieldStore.extractSendCallbackData(callback).end(null);
  }

  private static WeakReferences createSendWithRetainedCallback(SendCallback callback) {
    MessageAndWeakData association = createSendWithRetainedMessage(callback);
    return new WeakReferences(
        new WeakReference<>(association.message), association.data, association.callback);
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
    return createSendWithRetainedMessage(mock(SendCallback.class));
  }

  private static MessageAndWeakData createSendWithRetainedMessage(SendCallback callback) {
    Message<?> message = mock(Message.class);
    String topic = "persistent://public/default/abandoned-send";
    when(message.getTopicName()).thenReturn(topic);
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
