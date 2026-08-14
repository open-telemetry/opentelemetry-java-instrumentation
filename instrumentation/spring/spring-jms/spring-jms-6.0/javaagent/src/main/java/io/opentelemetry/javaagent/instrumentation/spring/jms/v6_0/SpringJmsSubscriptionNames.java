/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v6_0;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.jms.v3_0.JmsSubscriptionNames;
import jakarta.jms.Message;
import javax.annotation.Nullable;
import org.springframework.jms.listener.AbstractMessageListenerContainer;

public class SpringJmsSubscriptionNames {

  private static final VirtualField<AbstractMessageListenerContainer, String> SUBSCRIPTION_NAME =
      VirtualField.find(AbstractMessageListenerContainer.class, String.class);

  public static void set(
      AbstractMessageListenerContainer container, @Nullable String subscriptionName) {
    SUBSCRIPTION_NAME.set(container, subscriptionName);
  }

  public static void set(Message message, AbstractMessageListenerContainer container) {
    JmsSubscriptionNames.set(message, SUBSCRIPTION_NAME.get(container));
  }

  private SpringJmsSubscriptionNames() {}
}
