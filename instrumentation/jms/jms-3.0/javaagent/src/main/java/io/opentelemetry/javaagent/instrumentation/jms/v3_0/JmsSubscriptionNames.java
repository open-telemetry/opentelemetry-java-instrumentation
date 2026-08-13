/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v3_0;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageListener;
import javax.annotation.Nullable;

/**
 * Remembers the subscription name that a durable or shared consumer was created with, so that it
 * can be reported on the spans for the messages that the consumer delivers.
 *
 * <p>The name is copied from the consumer to the message listener when the listener is registered,
 * because providers dispatch messages to the listener without exposing the consumer they came from.
 * When the same listener is registered on several consumers, the name of the most recently
 * registered consumer wins.
 */
public final class JmsSubscriptionNames {

  private static final VirtualField<MessageConsumer, String> consumerSubscriptionName =
      VirtualField.find(MessageConsumer.class, String.class);
  private static final VirtualField<Message, String> messageSubscriptionName =
      VirtualField.find(Message.class, String.class);
  private static final VirtualField<MessageListener, String> listenerSubscriptionName =
      VirtualField.find(MessageListener.class, String.class);

  public static void set(MessageConsumer consumer, String subscriptionName) {
    consumerSubscriptionName.set(consumer, subscriptionName);
  }

  public static void set(Message message, @Nullable String subscriptionName) {
    messageSubscriptionName.set(message, subscriptionName);
  }

  public static void set(MessageListener messageListener, @Nullable String subscriptionName) {
    listenerSubscriptionName.set(messageListener, subscriptionName);
  }

  @Nullable
  public static String get(MessageConsumer consumer) {
    return consumerSubscriptionName.get(consumer);
  }

  @Nullable
  public static String get(Message message) {
    return messageSubscriptionName.get(message);
  }

  @Nullable
  public static String get(MessageListener messageListener) {
    return listenerSubscriptionName.get(messageListener);
  }

  private JmsSubscriptionNames() {}
}
