/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v1_1;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.jms.JmsListenerRegistrations;
import io.opentelemetry.javaagent.bootstrap.jms.JmsListenerRegistrations.Registration;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import javax.annotation.Nullable;
import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;

/**
 * Remembers the subscription name that a durable or shared consumer was created with, so that it
 * can be reported on the spans for the messages that the consumer delivers.
 *
 * <p>The name is copied from the consumer to the message listener when the listener is registered,
 * because providers dispatch messages to the listener without exposing the consumer they came from.
 * When the same listener is registered on several consumers, the most recent active registration
 * wins. Removing it exposes the preceding active registration.
 */
public class JmsSubscriptionNames {

  private static final VirtualField<MessageConsumer, String> CONSUMER_SUBSCRIPTION_NAME =
      VirtualField.find(MessageConsumer.class, String.class);
  private static final VirtualField<Message, String> MESSAGE_SUBSCRIPTION_NAME =
      VirtualField.find(Message.class, String.class);
  private static final VirtualField<MessageConsumer, ConsumerState> CONSUMER_STATE =
      VirtualField.find(MessageConsumer.class, ConsumerState.class);
  private static final VirtualField<Session, SessionRegistrations> SESSION_REGISTRATIONS =
      VirtualField.find(Session.class, SessionRegistrations.class);
  private static final VirtualField<Connection, ConnectionSessions> CONNECTION_SESSIONS =
      VirtualField.find(Connection.class, ConnectionSessions.class);
  private static final VirtualField<MessageListener, JmsListenerRegistrations>
      LISTENER_REGISTRATIONS =
          VirtualField.find(MessageListener.class, JmsListenerRegistrations.class);
  private static final ThreadLocal<RegistrationContext> listenerRegistrationContext =
      new ThreadLocal<>();

  public static void set(MessageConsumer consumer, String subscriptionName) {
    CONSUMER_SUBSCRIPTION_NAME.set(consumer, subscriptionName);
  }

  public static void set(Message message, @Nullable String subscriptionName) {
    MESSAGE_SUBSCRIPTION_NAME.set(message, subscriptionName);
  }

  public static void setSession(MessageConsumer consumer, Session session) {
    consumerState(consumer).setSessionRegistrations(sessionRegistrations(session));
  }

  public static void setConnection(Session session, Connection connection) {
    sessionRegistrations(session).setConnection(connectionSessions(connection));
  }

  public static void clearSession(Session session) {
    SessionRegistrations registrations = SESSION_REGISTRATIONS.get(session);
    if (registrations != null) {
      registrations.clear();
    }
  }

  public static void clearConnection(Connection connection) {
    ConnectionSessions sessions = CONNECTION_SESSIONS.get(connection);
    if (sessions != null) {
      sessions.clear();
    }
  }

  public static Object startListenerRegistration(
      MessageConsumer consumer, @Nullable MessageListener messageListener) {
    RegistrationContext context = listenerRegistrationContext.get();
    if (context == null) {
      context = new RegistrationContext();
      listenerRegistrationContext.set(context);
    }

    ListenerRegistrationChange change = null;
    try {
      ConsumerState state = consumerState(consumer);
      change =
          state.start(
              messageListener, CONSUMER_SUBSCRIPTION_NAME.get(consumer), context.get(state));
      if (change != null) {
        context.push(change);
      }
      return change;
    } finally {
      if (change == null && context.isEmpty()) {
        listenerRegistrationContext.remove();
      }
    }
  }

  public static void endListenerRegistration(
      @Nullable Object registrationChange, @Nullable Throwable throwable) {
    if (registrationChange == null) {
      return;
    }
    ListenerRegistrationChange change = (ListenerRegistrationChange) registrationChange;
    try {
      change.consumerState.end(change, throwable);
    } finally {
      RegistrationContext context = listenerRegistrationContext.get();
      if (context != null) {
        context.pop(change);
        if (context.isEmpty()) {
          listenerRegistrationContext.remove();
        }
      }
    }
  }

  public static void clearListenerRegistration(MessageConsumer consumer) {
    ConsumerState state = CONSUMER_STATE.get(consumer);
    if (state != null) {
      state.clear();
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
    JmsListenerRegistrations registrations = LISTENER_REGISTRATIONS.get(messageListener);
    return registrations == null ? null : registrations.getSubscriptionName();
  }

  private static JmsListenerRegistrations listenerRegistrations(MessageListener messageListener) {
    JmsListenerRegistrations registrations = LISTENER_REGISTRATIONS.get(messageListener);
    if (registrations != null) {
      return registrations;
    }
    synchronized (JmsListenerRegistrations.initializationLock()) {
      registrations = LISTENER_REGISTRATIONS.get(messageListener);
      if (registrations == null) {
        registrations = new JmsListenerRegistrations();
        LISTENER_REGISTRATIONS.set(messageListener, registrations);
      }
      return registrations;
    }
  }

  private static ConsumerState consumerState(MessageConsumer consumer) {
    ConsumerState state = CONSUMER_STATE.get(consumer);
    if (state != null) {
      return state;
    }
    synchronized (consumer) {
      state = CONSUMER_STATE.get(consumer);
      if (state == null) {
        state = new ConsumerState();
        CONSUMER_STATE.set(consumer, state);
      }
      return state;
    }
  }

  private static SessionRegistrations sessionRegistrations(Session session) {
    SessionRegistrations registrations = SESSION_REGISTRATIONS.get(session);
    if (registrations != null) {
      return registrations;
    }
    synchronized (session) {
      registrations = SESSION_REGISTRATIONS.get(session);
      if (registrations == null) {
        registrations = new SessionRegistrations();
        SESSION_REGISTRATIONS.set(session, registrations);
      }
      return registrations;
    }
  }

  private static ConnectionSessions connectionSessions(Connection connection) {
    ConnectionSessions sessions = CONNECTION_SESSIONS.get(connection);
    if (sessions != null) {
      return sessions;
    }
    synchronized (connection) {
      sessions = CONNECTION_SESSIONS.get(connection);
      if (sessions == null) {
        sessions = new ConnectionSessions();
        CONNECTION_SESSIONS.set(connection, sessions);
      }
      return sessions;
    }
  }

  private static void deactivate(@Nullable ConsumerListenerRegistration consumerRegistration) {
    if (consumerRegistration == null) {
      return;
    }
    consumerRegistration.registrations.deactivate(consumerRegistration.registration);
  }

  private static final class ConsumerState {
    private final Set<ListenerRegistrationChange> pendingChanges =
        Collections.newSetFromMap(new IdentityHashMap<>());
    @Nullable private SessionRegistrations sessionRegistrations;
    @Nullable private ConsumerListenerRegistration currentRegistration;
    private boolean trackedBySession;
    private boolean closed;

    private synchronized void setSessionRegistrations(SessionRegistrations registrations) {
      if (closed) {
        return;
      }
      sessionRegistrations = registrations;
      if ((currentRegistration != null || !pendingChanges.isEmpty()) && !trackedBySession) {
        if (registrations.add(this)) {
          trackedBySession = true;
        } else {
          clear();
        }
      }
    }

    @Nullable
    private synchronized ListenerRegistrationChange start(
        @Nullable MessageListener messageListener,
        @Nullable String subscriptionName,
        @Nullable ListenerRegistrationChange parent) {
      if (closed) {
        return null;
      }

      ConsumerListenerRegistration newRegistration = null;
      if (messageListener != null) {
        if (!trackedBySession && sessionRegistrations != null) {
          if (!sessionRegistrations.add(this)) {
            clear();
            return null;
          }
          trackedBySession = true;
        }
        JmsListenerRegistrations registrations = listenerRegistrations(messageListener);
        Registration registration = registrations.add(subscriptionName);
        newRegistration = new ConsumerListenerRegistration(registrations, registration);
      }

      ListenerRegistrationChange change =
          new ListenerRegistrationChange(this, currentRegistration, newRegistration, parent);
      pendingChanges.add(change);
      return change;
    }

    private synchronized void end(
        ListenerRegistrationChange change, @Nullable Throwable throwable) {
      if (!pendingChanges.remove(change) || closed) {
        deactivate(change.newRegistration);
        return;
      }
      if (throwable != null) {
        deactivate(change.newRegistration);
        if (currentRegistration == null) {
          stopSessionTracking();
        }
        return;
      }

      if (change.nestedRegistrationSucceeded) {
        deactivate(change.newRegistration);
      } else {
        currentRegistration = change.newRegistration;
        deactivate(change.previousRegistration);
      }
      change.markParentRegistrationSucceeded();
      if (currentRegistration == null) {
        stopSessionTracking();
      }
    }

    private synchronized void clear() {
      if (closed) {
        return;
      }
      closed = true;
      deactivate(currentRegistration);
      currentRegistration = null;
      for (ListenerRegistrationChange change : pendingChanges) {
        deactivate(change.newRegistration);
      }
      pendingChanges.clear();
      stopSessionTracking();
    }

    private void stopSessionTracking() {
      if (trackedBySession && sessionRegistrations != null) {
        sessionRegistrations.remove(this);
        trackedBySession = false;
      }
    }
  }

  private static final class ConsumerListenerRegistration {
    private final JmsListenerRegistrations registrations;
    private final Registration registration;

    private ConsumerListenerRegistration(
        JmsListenerRegistrations registrations, Registration registration) {
      this.registrations = registrations;
      this.registration = registration;
    }
  }

  private static final class ConnectionSessions {
    private final Set<SessionRegistrations> sessions =
        Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean closed;

    private synchronized boolean add(SessionRegistrations registrations) {
      if (closed) {
        return false;
      }
      sessions.add(registrations);
      return true;
    }

    private synchronized void remove(SessionRegistrations registrations) {
      sessions.remove(registrations);
    }

    private void clear() {
      SessionRegistrations[] registrations;
      synchronized (this) {
        if (closed) {
          return;
        }
        closed = true;
        registrations = sessions.toArray(new SessionRegistrations[0]);
        sessions.clear();
      }
      for (SessionRegistrations session : registrations) {
        session.clear();
      }
    }
  }

  private static final class SessionRegistrations {
    private final Set<ConsumerState> consumers = Collections.newSetFromMap(new IdentityHashMap<>());
    @Nullable private ConnectionSessions connection;
    private boolean closed;

    private void setConnection(ConnectionSessions connection) {
      if (!connection.add(this)) {
        clear();
        return;
      }
      synchronized (this) {
        if (closed) {
          connection.remove(this);
        } else {
          this.connection = connection;
        }
      }
    }

    private synchronized boolean add(ConsumerState consumer) {
      if (closed) {
        return false;
      }
      consumers.add(consumer);
      return true;
    }

    private synchronized void remove(ConsumerState consumer) {
      consumers.remove(consumer);
    }

    private void clear() {
      ConsumerState[] registeredConsumers;
      ConnectionSessions connection;
      synchronized (this) {
        if (closed) {
          return;
        }
        closed = true;
        registeredConsumers = consumers.toArray(new ConsumerState[0]);
        consumers.clear();
        connection = this.connection;
        this.connection = null;
      }
      if (connection != null) {
        connection.remove(this);
      }
      for (ConsumerState consumer : registeredConsumers) {
        consumer.clear();
      }
    }
  }

  private static final class ListenerRegistrationChange {
    private final ConsumerState consumerState;
    @Nullable private final ConsumerListenerRegistration previousRegistration;
    @Nullable private final ConsumerListenerRegistration newRegistration;
    @Nullable private final ListenerRegistrationChange parent;
    private boolean nestedRegistrationSucceeded;

    private ListenerRegistrationChange(
        ConsumerState consumerState,
        @Nullable ConsumerListenerRegistration previousRegistration,
        @Nullable ConsumerListenerRegistration newRegistration,
        @Nullable ListenerRegistrationChange parent) {
      this.consumerState = consumerState;
      this.previousRegistration = previousRegistration;
      this.newRegistration = newRegistration;
      this.parent = parent;
    }

    private void markParentRegistrationSucceeded() {
      ListenerRegistrationChange change = parent;
      while (change != null) {
        change.nestedRegistrationSucceeded = true;
        change = change.parent;
      }
    }
  }

  private static final class RegistrationContext {
    private final IdentityHashMap<ConsumerState, ListenerRegistrationChange> currentChanges =
        new IdentityHashMap<>();

    @Nullable
    private ListenerRegistrationChange get(ConsumerState state) {
      return currentChanges.get(state);
    }

    private void push(ListenerRegistrationChange change) {
      currentChanges.put(change.consumerState, change);
    }

    private void pop(ListenerRegistrationChange change) {
      if (currentChanges.get(change.consumerState) != change) {
        return;
      }
      if (change.parent == null) {
        currentChanges.remove(change.consumerState);
      } else {
        currentChanges.put(change.consumerState, change.parent);
      }
    }

    private boolean isEmpty() {
      return currentChanges.isEmpty();
    }
  }

  private JmsSubscriptionNames() {}
}
