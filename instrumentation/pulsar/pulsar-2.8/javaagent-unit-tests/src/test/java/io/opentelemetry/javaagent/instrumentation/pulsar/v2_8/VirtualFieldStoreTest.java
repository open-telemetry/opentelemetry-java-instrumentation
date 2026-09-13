/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import java.util.concurrent.CompletableFuture;
import org.apache.pulsar.client.api.Message;
import org.junit.jupiter.api.Test;

class VirtualFieldStoreTest {
  private static final ContextKey<String> TEST_KEY = ContextKey.named("test-key");

  @Test
  void keepsProcessParentSeparateFromConsumedMessageAccounting() {
    Message<?> message = mock(Message.class);
    Context processParent = Context.root().with(TEST_KEY, "parent");

    VirtualFieldStore.setReceiveState(message, processParent, true);
    assertThat(VirtualFieldStore.wereConsumedMessagesRecorded(message)).isTrue();
    assertThat(VirtualFieldStore.extract(message)).isSameAs(processParent);
  }

  @Test
  void replacesConsumedMessageAccountingWithoutChangingParent() {
    Message<?> message = mock(Message.class);
    Context processParent = Context.root().with(TEST_KEY, "parent");

    VirtualFieldStore.setReceiveState(message, processParent, true);
    VirtualFieldStore.setReceiveState(message, processParent, false);

    assertThat(VirtualFieldStore.wereConsumedMessagesRecorded(message)).isFalse();
    assertThat(VirtualFieldStore.extract(message)).isSameAs(processParent);
  }

  @Test
  void clearsStateWhenMessageIsRecycled() {
    Message<?> message = mock(Message.class);
    VirtualFieldStore.setReceiveState(message, Context.root(), true);

    VirtualFieldStore.clear(message);

    assertThat(VirtualFieldStore.wereConsumedMessagesRecorded(message)).isFalse();
  }

  @Test
  void carriesStateAcrossAsynchronousHandoff() throws Exception {
    Message<?> message = mock(Message.class);
    Context processParent = Context.root().with(TEST_KEY, "parent");
    VirtualFieldStore.setReceiveState(message, processParent, true);

    CompletableFuture<Boolean> consumedMessagesRecorded =
        CompletableFuture.supplyAsync(
            () ->
                VirtualFieldStore.extract(message) == processParent
                    && VirtualFieldStore.wereConsumedMessagesRecorded(message));

    assertThat(consumedMessagesRecorded.get()).isTrue();
  }
}
