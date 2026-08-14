/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v1_1;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;

/**
 * Remembers the subscription name that a durable or shared consumer was created with, so that it
 * can be reported on the spans for the messages that the consumer delivers.
 *
 * <p>The name is copied from the consumer to the message listener when the listener is registered,
 * because providers dispatch messages to the listener without exposing the consumer they came from.
 * Registering the same listener instance on several consumers reports the name of the most recently
 * registered consumer.
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

  /**
   * Records the subscription name of {@code consumer} on {@code messageListener} before the
   * registration is attempted, because providers can dispatch an already pending message to the
   * listener before the registration call returns. Returns a token that {@link
   * #completeListenerRegistration(Object, boolean)} uses to undo the change when the registration
   * turns out to have failed.
   */
  @Nullable
  public static Object copyToListener(
      MessageConsumer consumer, @Nullable MessageListener messageListener) {
    if (messageListener == null) {
      return null;
    }
    String previousName = LISTENER_SUBSCRIPTION_NAME.get(messageListener);
    String subscriptionName = CONSUMER_SUBSCRIPTION_NAME.get(consumer);
    LISTENER_SUBSCRIPTION_NAME.set(messageListener, subscriptionName);
    return new ListenerRegistration(messageListener, previousName, subscriptionName);
  }

  public static void completeListenerRegistration(
      @Nullable Object registration, boolean succeeded) {
    if (succeeded || !(registration instanceof ListenerRegistration)) {
      return;
    }
    ((ListenerRegistration) registration).undo();
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

  private static final class ListenerRegistration {

    private final MessageListener messageListener;
    @Nullable private final String previousName;
    @Nullable private final String subscriptionName;

    ListenerRegistration(
        MessageListener messageListener,
        @Nullable String previousName,
        @Nullable String subscriptionName) {
      this.messageListener = messageListener;
      this.previousName = previousName;
      this.subscriptionName = subscriptionName;
    }

    void undo() {
      // a concurrent registration that already replaced the name wins, its consumer is the one that
      // the listener ends up registered on
      if (Objects.equals(LISTENER_SUBSCRIPTION_NAME.get(messageListener), subscriptionName)) {
        LISTENER_SUBSCRIPTION_NAME.set(messageListener, previousName);
      }
    }
  }

  private JmsSubscriptionNames() {}
}
