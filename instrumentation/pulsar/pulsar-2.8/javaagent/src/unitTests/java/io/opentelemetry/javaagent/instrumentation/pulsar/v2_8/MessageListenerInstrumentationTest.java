/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageListener;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MessageListenerInstrumentationTest {
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

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @SuppressWarnings("unchecked")
  void propagatesProcessParentAndClosesScope(boolean fail) {
    Message<Object> message = mock(Message.class);
    when(message.getTopicName()).thenReturn("test-topic");
    Consumer<Object> consumer = mock(Consumer.class);
    when(consumer.getSubscription()).thenReturn("test-subscription");
    Context parent = Context.root().with(TEST_KEY, "parent");
    Context previous = Context.current();
    VirtualFieldStore.setProcessParentContext(message, parent);
    IllegalStateException failure = new IllegalStateException("listener failed");
    MessageListener<Object> delegate =
        (observedConsumer, observedMessage) -> {
          assertThat(observedConsumer).isSameAs(consumer);
          assertThat(observedMessage).isSameAs(message);
          assertThat(Context.current().get(TEST_KEY)).isEqualTo("parent");
          if (fail) {
            throw failure;
          }
        };
    MessageListener<Object> wrapper =
        new MessageListenerInstrumentation.MessageListenerWrapper<>(delegate);

    if (fail) {
      assertThatThrownBy(() -> wrapper.received(consumer, message)).isSameAs(failure);
    } else {
      wrapper.received(consumer, message);
    }
    assertThat(Context.current()).isSameAs(previous);
  }
}
