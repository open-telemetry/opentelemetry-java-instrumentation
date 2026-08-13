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
 * When the same listener is registered on several consumers, the most recent active registration
 * wins. Removing it exposes the preceding active registration.
 */
public final class JmsSubscriptionNames {

  private static final VirtualField<MessageConsumer, String> CONSUMER_SUBSCRIPTION_NAME =
      VirtualField.find(MessageConsumer.class, String.class);
  private static final VirtualField<Message, String> MESSAGE_SUBSCRIPTION_NAME =
      VirtualField.find(Message.class, String.class);
  private static final VirtualField<MessageConsumer, ConsumerListenerRegistration>
      CONSUMER_LISTENER_REGISTRATION =
          VirtualField.find(MessageConsumer.class, ConsumerListenerRegistration.class);
  private static final VirtualField<MessageListener, ListenerRegistrations> LISTENER_REGISTRATIONS =
      VirtualField.find(MessageListener.class, ListenerRegistrations.class);

  public static void set(MessageConsumer consumer, String subscriptionName) {
    CONSUMER_SUBSCRIPTION_NAME.set(consumer, subscriptionName);
  }

  public static void set(Message message, @Nullable String subscriptionName) {
    MESSAGE_SUBSCRIPTION_NAME.set(message, subscriptionName);
  }

  public static Object startListenerRegistration(
      MessageConsumer consumer, @Nullable MessageListener messageListener) {
    ConsumerListenerRegistration previousRegistration =
        CONSUMER_LISTENER_REGISTRATION.get(consumer);
    ConsumerListenerRegistration newRegistration = null;
    if (messageListener != null) {
      ListenerRegistrations registrations = listenerRegistrations(messageListener);
      ListenerRegistration registration =
          registrations.add(CONSUMER_SUBSCRIPTION_NAME.get(consumer));
      newRegistration = new ConsumerListenerRegistration(registrations, registration);
    }
    return new ListenerRegistrationChange(consumer, previousRegistration, newRegistration);
  }

  public static void endListenerRegistration(
      @Nullable Object registrationChange, @Nullable Throwable throwable) {
    if (registrationChange == null) {
      return;
    }
    ListenerRegistrationChange change = (ListenerRegistrationChange) registrationChange;
    if (throwable != null) {
      deactivate(change.newRegistration);
      return;
    }

    CONSUMER_LISTENER_REGISTRATION.set(change.consumer, change.newRegistration);
    deactivate(change.previousRegistration);
  }

  public static void clearListenerRegistration(MessageConsumer consumer) {
    ConsumerListenerRegistration registration = CONSUMER_LISTENER_REGISTRATION.get(consumer);
    CONSUMER_LISTENER_REGISTRATION.set(consumer, null);
    deactivate(registration);
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
    ListenerRegistrations registrations = LISTENER_REGISTRATIONS.get(messageListener);
    return registrations == null ? null : registrations.getSubscriptionName();
  }

  private static ListenerRegistrations listenerRegistrations(MessageListener messageListener) {
    ListenerRegistrations registrations = LISTENER_REGISTRATIONS.get(messageListener);
    if (registrations != null) {
      return registrations;
    }
    synchronized (messageListener) {
      registrations = LISTENER_REGISTRATIONS.get(messageListener);
      if (registrations == null) {
        registrations = new ListenerRegistrations();
        LISTENER_REGISTRATIONS.set(messageListener, registrations);
      }
      return registrations;
    }
  }

  @Nullable
  private static ListenerRegistration activeRegistration(
      @Nullable ListenerRegistration registration) {
    while (registration != null && !registration.active) {
      registration = registration.previous;
    }
    return registration;
  }

  private static void deactivate(@Nullable ConsumerListenerRegistration consumerRegistration) {
    if (consumerRegistration == null) {
      return;
    }
    consumerRegistration.registrations.deactivate(consumerRegistration.registration);
  }

  private static final class ConsumerListenerRegistration {
    private final ListenerRegistrations registrations;
    private final ListenerRegistration registration;

    private ConsumerListenerRegistration(
        ListenerRegistrations registrations, ListenerRegistration registration) {
      this.registrations = registrations;
      this.registration = registration;
    }
  }

  private static final class ListenerRegistrations {
    @Nullable private volatile ListenerRegistration current;

    private synchronized ListenerRegistration add(@Nullable String subscriptionName) {
      ListenerRegistration registration =
          new ListenerRegistration(subscriptionName, activeRegistration(current));
      current = registration;
      return registration;
    }

    @Nullable
    private String getSubscriptionName() {
      ListenerRegistration registration = activeRegistration(current);
      return registration == null ? null : registration.subscriptionName;
    }

    private synchronized void deactivate(ListenerRegistration registration) {
      registration.active = false;
      if (current == registration) {
        current = activeRegistration(registration.previous);
      }
    }
  }

  private static final class ListenerRegistration {
    @Nullable private final String subscriptionName;
    @Nullable private final ListenerRegistration previous;
    private volatile boolean active = true;

    private ListenerRegistration(
        @Nullable String subscriptionName, @Nullable ListenerRegistration previous) {
      this.subscriptionName = subscriptionName;
      this.previous = previous;
    }
  }

  private static final class ListenerRegistrationChange {
    private final MessageConsumer consumer;
    @Nullable private final ConsumerListenerRegistration previousRegistration;
    @Nullable private final ConsumerListenerRegistration newRegistration;

    private ListenerRegistrationChange(
        MessageConsumer consumer,
        @Nullable ConsumerListenerRegistration previousRegistration,
        @Nullable ConsumerListenerRegistration newRegistration) {
      this.consumer = consumer;
      this.previousRegistration = previousRegistration;
      this.newRegistration = newRegistration;
    }
  }

  private JmsSubscriptionNames() {}
}
