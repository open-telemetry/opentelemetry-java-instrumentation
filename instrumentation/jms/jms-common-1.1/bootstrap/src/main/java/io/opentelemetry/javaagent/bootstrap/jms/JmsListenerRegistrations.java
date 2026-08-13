/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.jms;

import javax.annotation.Nullable;

/** Stores JMS listener registration state shared across application classloaders. */
public final class JmsListenerRegistrations {
  private static final Object initializationLock = new Object();

  @Nullable private volatile Registration current;

  public static Object initializationLock() {
    return initializationLock;
  }

  public synchronized Registration add(@Nullable String subscriptionName) {
    Registration previous = current;
    Registration registration = new Registration(subscriptionName, previous);
    if (previous != null) {
      previous.next = registration;
    }
    current = registration;
    return registration;
  }

  @Nullable
  public String getSubscriptionName() {
    Registration registration = current;
    return registration == null ? null : registration.subscriptionName;
  }

  public synchronized void deactivate(Registration registration) {
    if (!registration.active) {
      return;
    }
    registration.active = false;

    Registration previous = registration.previous;
    Registration next = registration.next;
    if (previous != null) {
      previous.next = next;
    }
    if (next == null) {
      current = previous;
    } else {
      next.previous = previous;
    }
    registration.previous = null;
    registration.next = null;
  }

  public static final class Registration {
    @Nullable private final String subscriptionName;
    @Nullable private Registration previous;
    @Nullable private Registration next;
    private boolean active = true;

    private Registration(@Nullable String subscriptionName, @Nullable Registration previous) {
      this.subscriptionName = subscriptionName;
      this.previous = previous;
    }
  }
}
