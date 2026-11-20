/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v1_1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.jms.JmsMessageProcessingState;
import io.opentelemetry.javaagent.instrumentation.jms.common.v1_1.MessageAdapter;
import java.lang.reflect.Proxy;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import org.junit.jupiter.api.Test;

class JmsMessageListenerStartFailureTest {

  @Test
  void releasesProcessingWhenListenerSetupFails() {
    Message message = newMessage();
    MessageListener listener = ignored -> {};
    MessageConsumer consumer = newMessageConsumer();
    JmsSubscriptionNames.set(consumer, "subscription");
    JmsSubscriptionNames.copyToListener(consumer, listener);
    IllegalStateException failure = new IllegalStateException("failure");

    try (Scope ignored = throwingContext(failure).makeCurrent()) {
      assertThatThrownBy(
              () ->
                  JmsMessageListenerInstrumentation.MessageListenerAdvice.AdviceScope.start(
                      listener, message))
          .isSameAs(failure);
    }

    assertTwoProcessingLifecyclesCanStart(JavaxMessageAdapter.create(message));
    assertThat(JmsSubscriptionNames.get(message)).isNull();
  }

  @Test
  void camelOwnedListenerStillCompletesProcessingLifecycle() {
    VirtualField<MessageListener, Boolean> camelOwnsProcessing =
        VirtualField.find(MessageListener.class, Boolean.class);
    VirtualField<Message, JmsMessageProcessingState> processingState =
        VirtualField.find(Message.class, JmsMessageProcessingState.class);
    Message message = newMessage();
    MessageListener listener = ignored -> {};
    camelOwnsProcessing.set(listener, true);

    JmsMessageListenerInstrumentation.MessageListenerAdvice.AdviceScope firstScope =
        JmsMessageListenerInstrumentation.MessageListenerAdvice.AdviceScope.start(
            listener, message);
    assertThat(firstScope).isNotNull();
    JmsMessageProcessingState firstState = processingState.get(message);
    firstScope.end(null);
    assertThat(firstState.isProcessingCompleted()).isTrue();

    JmsMessageListenerInstrumentation.MessageListenerAdvice.AdviceScope secondScope =
        JmsMessageListenerInstrumentation.MessageListenerAdvice.AdviceScope.start(
            listener, message);
    assertThat(secondScope).isNotNull();
    JmsMessageProcessingState secondState = processingState.get(message);
    assertThat(secondState).isNotSameAs(firstState);
    secondScope.end(null);
    assertThat(secondState.isProcessingCompleted()).isTrue();
  }

  private static void assertTwoProcessingLifecyclesCanStart(MessageAdapter adapter) {
    for (int i = 0; i < 2; i++) {
      assertThat(adapter.beginProcessing()).isTrue();
      adapter.endProcessing();
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
    return newProxy(Message.class);
  }

  private static MessageConsumer newMessageConsumer() {
    return newProxy(MessageConsumer.class);
  }

  private static <T> T newProxy(Class<T> type) {
    return type.cast(
        Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            (proxy, method, args) -> {
              if (method.getName().equals("hashCode")) {
                return System.identityHashCode(proxy);
              }
              if (method.getName().equals("equals")) {
                return proxy == args[0];
              }
              return null;
            }));
  }
}
