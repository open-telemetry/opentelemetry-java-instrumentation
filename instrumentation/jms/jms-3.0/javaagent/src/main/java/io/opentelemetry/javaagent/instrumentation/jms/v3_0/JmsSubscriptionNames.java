/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v3_0;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageListener;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

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

  private static final int COMMITTED_NAME = 0;
  private static final int PENDING_REGISTRATIONS = 1;
  private static final int REGISTRATION_LISTENER = 0;
  private static final int REGISTRATION_STATE = 1;
  private static final int REGISTRATION_NAME = 2;

  private static final VirtualField<MessageConsumer, String> CONSUMER_SUBSCRIPTION_NAME =
      VirtualField.find(MessageConsumer.class, String.class);
  private static final VirtualField<Message, String> MESSAGE_SUBSCRIPTION_NAME =
      VirtualField.find(Message.class, String.class);
  private static final VirtualField<MessageListener, Object[]> LISTENER_STATE =
      VirtualField.find(MessageListener.class, Object[].class);

  public static void set(MessageConsumer consumer, String subscriptionName) {
    CONSUMER_SUBSCRIPTION_NAME.set(consumer, subscriptionName);
  }

  public static void set(Message message, @Nullable String subscriptionName) {
    MESSAGE_SUBSCRIPTION_NAME.set(message, subscriptionName);
  }

  @Nullable
  public static Object copyToListener(
      MessageConsumer consumer, @Nullable MessageListener messageListener) {
    if (messageListener == null) {
      return null;
    }
    synchronized (messageListener) {
      Object[] state = LISTENER_STATE.get(messageListener);
      if (state == null) {
        // Bootstrap types keep the state usable when a listener comes from a child class loader.
        state = new Object[] {null, new ArrayList<Object[]>()};
        LISTENER_STATE.set(messageListener, state);
      }
      Object[] registration =
          new Object[] {messageListener, state, CONSUMER_SUBSCRIPTION_NAME.get(consumer)};
      pendingRegistrations(state).add(registration);
      return registration;
    }
  }

  public static void completeListenerRegistration(
      @Nullable Object registration, boolean succeeded) {
    if (!(registration instanceof Object[])) {
      return;
    }
    Object[] listenerRegistration = (Object[]) registration;
    if (listenerRegistration.length != 3
        || !(listenerRegistration[REGISTRATION_LISTENER] instanceof MessageListener)
        || !(listenerRegistration[REGISTRATION_STATE] instanceof Object[])) {
      return;
    }
    MessageListener listener = (MessageListener) listenerRegistration[REGISTRATION_LISTENER];
    synchronized (listener) {
      Object[] state = LISTENER_STATE.get(listener);
      if (state != listenerRegistration[REGISTRATION_STATE]) {
        return;
      }
      List<Object[]> pending = pendingRegistrations(state);
      int index = pending.indexOf(listenerRegistration);
      if (index < 0) {
        return;
      }
      if (succeeded) {
        state[COMMITTED_NAME] = listenerRegistration[REGISTRATION_NAME];
        pending.subList(0, index + 1).clear();
      } else {
        pending.remove(index);
      }
    }
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
    synchronized (messageListener) {
      Object[] state = LISTENER_STATE.get(messageListener);
      if (state == null) {
        return null;
      }
      List<Object[]> pending = pendingRegistrations(state);
      return (String)
          (pending.isEmpty()
              ? state[COMMITTED_NAME]
              : pending.get(pending.size() - 1)[REGISTRATION_NAME]);
    }
  }

  @SuppressWarnings("unchecked") // initialized with a List<Object[]> in copyToListener
  private static List<Object[]> pendingRegistrations(Object[] state) {
    return (List<Object[]>) state[PENDING_REGISTRATIONS];
  }

  private JmsSubscriptionNames() {}
}
