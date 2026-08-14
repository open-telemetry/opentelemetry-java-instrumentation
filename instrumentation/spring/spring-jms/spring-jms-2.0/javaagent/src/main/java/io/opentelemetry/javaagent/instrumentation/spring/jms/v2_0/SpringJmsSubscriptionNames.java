/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v2_0;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.jms.v1_1.JmsSubscriptionNames;
import javax.annotation.Nullable;
import javax.jms.Message;
import org.springframework.jms.listener.AbstractMessageListenerContainer;

public class SpringJmsSubscriptionNames {

  private static final VirtualField<AbstractMessageListenerContainer, SubscriptionState>
      SUBSCRIPTION_STATE =
          VirtualField.find(AbstractMessageListenerContainer.class, SubscriptionState.class);
  // an agent owned lock, so that the agent never blocks on a monitor that application code holds
  private static final Object initializationLock = new Object();

  public static void set(
      AbstractMessageListenerContainer container, @Nullable String subscriptionName) {
    state(container).subscriptionName = subscriptionName;
  }

  public static void set(Message message, AbstractMessageListenerContainer container) {
    SubscriptionState state = SUBSCRIPTION_STATE.get(container);
    String subscriptionName = null;
    if (state != null
        && container.isPubSubDomain()
        && (state.subscriptionDurable || state.subscriptionShared)) {
      subscriptionName = state.subscriptionName;
    }
    JmsSubscriptionNames.set(message, subscriptionName);
  }

  public static void setDurable(
      AbstractMessageListenerContainer container, boolean subscriptionDurable) {
    state(container).subscriptionDurable = subscriptionDurable;
  }

  public static void setShared(
      AbstractMessageListenerContainer container, boolean subscriptionShared) {
    state(container).subscriptionShared = subscriptionShared;
  }

  private static SubscriptionState state(AbstractMessageListenerContainer container) {
    SubscriptionState state = SUBSCRIPTION_STATE.get(container);
    if (state != null) {
      return state;
    }
    synchronized (initializationLock) {
      state = SUBSCRIPTION_STATE.get(container);
      if (state == null) {
        state = new SubscriptionState();
        SUBSCRIPTION_STATE.set(container, state);
      }
      return state;
    }
  }

  private SpringJmsSubscriptionNames() {}

  private static class SubscriptionState {
    private volatile boolean subscriptionDurable;
    private volatile boolean subscriptionShared;
    @Nullable private volatile String subscriptionName;
  }
}
