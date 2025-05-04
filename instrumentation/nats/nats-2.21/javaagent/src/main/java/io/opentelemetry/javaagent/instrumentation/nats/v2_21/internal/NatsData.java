/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nats.v2_21.internal;

import io.nats.client.Connection;
import io.nats.client.Subscription;
import io.opentelemetry.instrumentation.api.util.VirtualField;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.", or "This class is internal and experimental. Its APIs are unstable and can change at
 * any time. Its APIs (or a version of them) may be promoted to the public stable API in the future,
 * but no guarantees are made.
 */
public final class NatsData {

  private static final VirtualField<Subscription, Connection> subscriptionConnection =
      VirtualField.find(Subscription.class, Connection.class);

  public static void addSubscription(Subscription subscription, Connection connection) {
    subscriptionConnection.set(subscription, connection);
  }

  public static void removeSubscription(Subscription subscription) {
    subscriptionConnection.set(subscription, null);
  }

  public static Connection getConnection(Subscription subscription) {
    return subscriptionConnection.get(subscription);
  }

  private NatsData() {}
}
