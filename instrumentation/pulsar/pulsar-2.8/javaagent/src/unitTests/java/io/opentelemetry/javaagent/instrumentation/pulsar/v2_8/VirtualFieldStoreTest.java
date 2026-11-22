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
import io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry.PulsarRequest;
import java.util.concurrent.CompletableFuture;
import org.apache.pulsar.client.api.Message;
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
    Context context = Context.root().with(TEST_KEY, "send");
    PulsarRequest request = mock(PulsarRequest.class);

    VirtualFieldStore.inject(callback, context, request);

    SendCallbackData callbackData = VirtualFieldStore.takeSendCallbackData(callback);
    assertThat(callbackData.context).isSameAs(context);
    assertThat(callbackData.request).isSameAs(request);
    assertThat(VirtualFieldStore.takeSendCallbackData(callback)).isNull();
  }
}
