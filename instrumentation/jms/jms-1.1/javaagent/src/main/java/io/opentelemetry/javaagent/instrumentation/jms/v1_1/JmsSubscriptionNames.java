/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v1_1;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.annotation.Nullable;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;

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

  private static final int SUBSCRIPTION_NAME = 0;
  private static final int PREVIOUS_REGISTRATION = 1;
  private static final int FAILED = 2;

  private static final VirtualField<MessageConsumer, String> CONSUMER_SUBSCRIPTION_NAME =
      VirtualField.find(MessageConsumer.class, String.class);
  private static final VirtualField<Message, String> MESSAGE_SUBSCRIPTION_NAME =
      VirtualField.find(Message.class, String.class);
  private static final VirtualField<MessageListener, String> LISTENER_SUBSCRIPTION_NAME =
      VirtualField.find(MessageListener.class, String.class);
  private static final VirtualField<MessageListener, Object[]> LISTENER_REGISTRATION =
      VirtualField.find(MessageListener.class, Object[].class);
  // an agent owned lock, so that the agent never blocks on a monitor that application code holds
  private static final Object registrationLock = new Object();

  public static void set(MessageConsumer consumer, String subscriptionName) {
    CONSUMER_SUBSCRIPTION_NAME.set(consumer, subscriptionName);
  }

  public static void set(Message message, @Nullable String subscriptionName) {
    MESSAGE_SUBSCRIPTION_NAME.set(message, subscriptionName);
  }

  public static Object beginListenerRegistration(
      MessageConsumer consumer, MessageListener messageListener) {
    synchronized (registrationLock) {
      Object[] registration = {
        CONSUMER_SUBSCRIPTION_NAME.get(consumer),
        currentRegistration(LISTENER_REGISTRATION.get(messageListener)),
        false
      };
      setCurrentRegistration(messageListener, registration);
      return registration;
    }
  }

  public static void endListenerRegistration(
      MessageListener messageListener, Object registrationToken, @Nullable Throwable throwable) {
    Object[] registration = (Object[]) registrationToken;
    synchronized (registrationLock) {
      if (throwable == null) {
        registration[PREVIOUS_REGISTRATION] = null;
        return;
      }
      registration[FAILED] = true;
      if (LISTENER_REGISTRATION.get(messageListener) == registration) {
        setCurrentRegistration(
            messageListener, currentRegistration((Object[]) registration[PREVIOUS_REGISTRATION]));
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
    return LISTENER_SUBSCRIPTION_NAME.get(messageListener);
  }

  @Nullable
  private static Object[] currentRegistration(@Nullable Object[] registration) {
    while (registration != null && Boolean.TRUE.equals(registration[FAILED])) {
      registration = (Object[]) registration[PREVIOUS_REGISTRATION];
    }
    return registration;
  }

  private static void setCurrentRegistration(
      MessageListener messageListener, @Nullable Object[] registration) {
    LISTENER_REGISTRATION.set(messageListener, registration);
    LISTENER_SUBSCRIPTION_NAME.set(
        messageListener, registration == null ? null : (String) registration[SUBSCRIPTION_NAME]);
  }

  private JmsSubscriptionNames() {}
}
