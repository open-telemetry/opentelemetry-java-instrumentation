/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import static io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry.PulsarSingletons.listenerProcessingSelection;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.internal.ScopedThreadSuppression;
import io.opentelemetry.instrumentation.api.internal.Timer;
import io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.VirtualFieldStore;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import java.util.concurrent.CompletableFuture;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PulsarSingletonsTest {
  private static final ContextKey<String> TEST_KEY = ContextKey.named("test-key");
  private static OpenTelemetrySdk openTelemetry;

  @BeforeAll
  static void setUp() {
    openTelemetry = OpenTelemetrySdk.builder().build();
    GlobalOpenTelemetry.set(openTelemetry);
  }

  @AfterAll
  static void tearDown() {
    openTelemetry.close();
    GlobalOpenTelemetry.resetForTest();
  }

  @Test
  void nestedListenerProcessingSelectionPreservesOuterSelection() {
    ScopedThreadSuppression selection = listenerProcessingSelection();
    boolean outerSelectionAcquired = selection.tryAcquire();
    try {
      assertThat(outerSelectionAcquired).isTrue();
      boolean innerSelectionAcquired = selection.tryAcquire();
      assertThat(innerSelectionAcquired).isFalse();
      if (innerSelectionAcquired) {
        selection.release();
      }
      assertThat(selection.isActive()).isTrue();
    } finally {
      if (outerSelectionAcquired) {
        selection.release();
      }
    }
    assertThat(selection.isActive()).isFalse();
  }

  @Test
  void listenerProcessingSelectionReleasesAfterException() {
    ScopedThreadSuppression selection = listenerProcessingSelection();
    boolean selectionAcquired = selection.tryAcquire();

    assertThatIllegalStateException()
        .isThrownBy(
            () -> {
              try {
                throw new IllegalStateException("boom");
              } finally {
                if (selectionAcquired) {
                  selection.release();
                }
              }
            });

    assertThat(selection.isActive()).isFalse();
  }

  @Test
  void failedReceivePreservesProcessParent() {
    Message<?> message = mock(Message.class);
    when(message.getTopicName()).thenReturn("test-topic");
    Consumer<?> consumer = mock(Consumer.class);
    when(consumer.getSubscription()).thenReturn("test-subscription");
    Context parent = Context.root().with(TEST_KEY, "parent");

    PulsarSingletons.startAndEndConsumerReceive(
        parent, message, Timer.start(), consumer, new IllegalStateException("receive failed"));

    assertThat(VirtualFieldStore.extractProcessParentContext(message).get(TEST_KEY))
        .isEqualTo("parent");
  }

  @Test
  void asynchronousListenerReceivePropagatesCapturedParent() {
    Message<?> message = mock(Message.class);
    when(message.getTopicName()).thenReturn("test-topic");
    Consumer<?> consumer = mock(Consumer.class);
    when(consumer.getSubscription()).thenReturn("test-subscription");
    Context parent = Context.root().with(TEST_KEY, "parent");
    CompletableFuture<Message<?>> future = new CompletableFuture<>();
    CompletableFuture<Message<?>> wrapped;
    boolean acquired = listenerProcessingSelection().tryAcquire();
    try (Scope ignored = parent.makeCurrent()) {
      wrapped = PulsarSingletons.wrap(future, Timer.start(), consumer);
    } finally {
      if (acquired) {
        listenerProcessingSelection().release();
      }
    }
    CompletableFuture<String> callbackContext =
        wrapped.thenApply(unused -> Context.current().get(TEST_KEY));

    CompletableFuture.runAsync(() -> future.complete(message)).join();

    assertThat(wrapped.join()).isSameAs(message);
    assertThat(callbackContext.join()).isEqualTo("parent");
    assertThat(VirtualFieldStore.extractProcessParentContext(message).get(TEST_KEY))
        .isEqualTo("parent");
    assertThat(listenerProcessingSelection().isActive()).isFalse();
  }
}
