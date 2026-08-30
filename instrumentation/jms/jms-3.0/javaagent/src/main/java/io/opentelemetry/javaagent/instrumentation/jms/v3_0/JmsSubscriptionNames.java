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
 * <p>The name is copied from the consumer to the message listener before registering the listener,
 * because providers may dispatch messages before registration returns without exposing the consumer
 * they came from. Registering the same listener instance on several consumers reports the name of
 * the most recently registered consumer, and closing a consumer doesn't restore the name of an
 * earlier one, because the listener is shared and there is no owner to hand it back to.
 */
public class JmsSubscriptionNames {

  private static final int STATE_LOCK_INDEX = 0;
  private static final int CURRENT_REGISTRATION_INDEX = 1;
  private static final int REGISTRATION_SUBSCRIPTION_NAME_INDEX = 0;
  private static final int REGISTRATION_PREVIOUS_INDEX = 1;
  private static final int REGISTRATION_FAILED_INDEX = 2;

  private static final VirtualField<MessageConsumer, String> CONSUMER_SUBSCRIPTION_NAME =
      VirtualField.find(MessageConsumer.class, String.class);
  private static final VirtualField<Message, String> MESSAGE_SUBSCRIPTION_NAME =
      VirtualField.find(Message.class, String.class);
  private static final VirtualField<MessageListener, Object[]> LISTENER_SUBSCRIPTION_STATE =
      VirtualField.find(MessageListener.class, Object[].class);

  public static void set(MessageConsumer consumer, String subscriptionName) {
    CONSUMER_SUBSCRIPTION_NAME.set(consumer, subscriptionName);
  }

  public static void set(Message message, @Nullable String subscriptionName) {
    MESSAGE_SUBSCRIPTION_NAME.set(message, subscriptionName);
  }

  @Nullable
  public static Object beginListenerRegistration(
      MessageConsumer consumer, @Nullable MessageListener messageListener) {
    if (messageListener == null) {
      return null;
    }
    Object[] state = listenerState(messageListener);
    synchronized (state[STATE_LOCK_INDEX]) {
      Object[] registration = {
        CONSUMER_SUBSCRIPTION_NAME.get(consumer), state[CURRENT_REGISTRATION_INDEX], false
      };
      state[CURRENT_REGISTRATION_INDEX] = registration;
      return registration;
    }
  }

  public static void endListenerRegistration(
      @Nullable MessageListener messageListener,
      @Nullable Object registrationToken,
      boolean succeeded) {
    if (messageListener == null || registrationToken == null) {
      return;
    }
    Object[] registration = (Object[]) registrationToken;
    Object[] state = listenerState(messageListener);
    synchronized (state[STATE_LOCK_INDEX]) {
      if (succeeded) {
        registration[REGISTRATION_PREVIOUS_INDEX] = null;
        return;
      }

      registration[REGISTRATION_FAILED_INDEX] = true;
      if (state[CURRENT_REGISTRATION_INDEX] != registration) {
        return;
      }

      Object[] previous = (Object[]) registration[REGISTRATION_PREVIOUS_INDEX];
      while (previous != null && Boolean.TRUE.equals(previous[REGISTRATION_FAILED_INDEX])) {
        Object[] failedRegistration = previous;
        previous = (Object[]) previous[REGISTRATION_PREVIOUS_INDEX];
        failedRegistration[REGISTRATION_PREVIOUS_INDEX] = null;
      }
      state[CURRENT_REGISTRATION_INDEX] = previous;
      registration[REGISTRATION_PREVIOUS_INDEX] = null;
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
    Object[] state = LISTENER_SUBSCRIPTION_STATE.get(messageListener);
    if (state == null) {
      return null;
    }
    synchronized (state[STATE_LOCK_INDEX]) {
      Object[] registration = (Object[]) state[CURRENT_REGISTRATION_INDEX];
      return registration == null
          ? null
          : (String) registration[REGISTRATION_SUBSCRIPTION_NAME_INDEX];
    }
  }

  private static Object[] listenerState(MessageListener messageListener) {
    Object[] state = LISTENER_SUBSCRIPTION_STATE.get(messageListener);
    if (state != null) {
      return state;
    }
    synchronized (LISTENER_SUBSCRIPTION_STATE) {
      state = LISTENER_SUBSCRIPTION_STATE.get(messageListener);
      if (state == null) {
        state = new Object[] {new Object(), null};
        LISTENER_SUBSCRIPTION_STATE.set(messageListener, state);
      }
      return state;
    }
  }

  private JmsSubscriptionNames() {}
}
