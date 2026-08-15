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
 * <p>The name is copied from the consumer to the message listener once the listener has been
 * registered, because providers dispatch messages to the listener without exposing the consumer
 * they came from. Registering the same listener instance on several consumers reports the name of
 * the most recently registered consumer, and closing a consumer doesn't restore the name of an
 * earlier one, because the listener is shared and there is no owner to hand it back to.
 */
public class JmsSubscriptionNames {

  private static final VirtualField<MessageConsumer, String> CONSUMER_SUBSCRIPTION_NAME =
      VirtualField.find(MessageConsumer.class, String.class);
  private static final VirtualField<Message, String> MESSAGE_SUBSCRIPTION_NAME =
      VirtualField.find(Message.class, String.class);
  private static final VirtualField<MessageListener, String> LISTENER_SUBSCRIPTION_NAME =
      VirtualField.find(MessageListener.class, String.class);

  public static void set(MessageConsumer consumer, String subscriptionName) {
    CONSUMER_SUBSCRIPTION_NAME.set(consumer, subscriptionName);
  }

  public static void set(Message message, @Nullable String subscriptionName) {
    MESSAGE_SUBSCRIPTION_NAME.set(message, subscriptionName);
  }

  public static void copyToListener(
      MessageConsumer consumer, @Nullable MessageListener messageListener) {
    if (messageListener == null) {
      return;
    }
    LISTENER_SUBSCRIPTION_NAME.set(messageListener, CONSUMER_SUBSCRIPTION_NAME.get(consumer));
  }

  @Nullable
  public static String get(MessageConsumer consumer) {
    return CONSUMER_SUBSCRIPTION_NAME.get(consumer);
  }

  @Nullable
  public static String get(Message message) {
    return MESSAGE_SUBSCRIPTION_NAME.get(message);
  }

  @Nullable
  public static String get(MessageListener messageListener) {
    return LISTENER_SUBSCRIPTION_NAME.get(messageListener);
  }

  private JmsSubscriptionNames() {}
}
