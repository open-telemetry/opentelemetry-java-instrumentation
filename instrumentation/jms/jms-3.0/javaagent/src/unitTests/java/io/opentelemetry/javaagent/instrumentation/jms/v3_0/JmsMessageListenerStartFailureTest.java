/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v3_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.jms.common.v1_1.MessageAdapter;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

class JmsMessageListenerStartFailureTest {

  @Test
  void releasesProcessingWhenListenerSetupFails() {
    Message message = newMessage();
    MessageListener listener = ignored -> {};
    IllegalStateException failure = new IllegalStateException("failure");

    try (Scope ignored = throwingContext(failure).makeCurrent()) {
      assertThatThrownBy(
              () ->
                  JmsMessageListenerInstrumentation.MessageListenerAdvice.AdviceScope.start(
                      listener, message))
          .isSameAs(failure);
    }

    assertTwoNewDeliveriesCanClaimConsumedMessages(JakartaMessageAdapter.create(message));
  }

  private static void assertTwoNewDeliveriesCanClaimConsumedMessages(MessageAdapter adapter) {
    for (int i = 0; i < 2; i++) {
      adapter.beginProcessing();
      try {
        assertThat(adapter.claimConsumedMessages()).isTrue();
      } finally {
        adapter.endProcessing();
      }
    }
  }

  private static Context throwingContext(RuntimeException failure) {
    return new Context() {
      @Override
      public <V> V get(ContextKey<V> key) {
        throw failure;
      }

      @Override
      public <V> Context with(ContextKey<V> key, V value) {
        return this;
      }
    };
  }

  private static Message newMessage() {
    return (Message)
        Proxy.newProxyInstance(
            Message.class.getClassLoader(),
            new Class<?>[] {Message.class},
            (proxy, method, args) -> {
              if (method.getName().equals("hashCode")) {
                return System.identityHashCode(proxy);
              }
              if (method.getName().equals("equals")) {
                return proxy == args[0];
              }
              return null;
            });
  }
}
