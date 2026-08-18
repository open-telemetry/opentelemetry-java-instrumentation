/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import java.util.List;

/**
 * Identifies the source of a delivery and remembers which of its deliveries a process operation
 * failed to handle.
 *
 * <p>When a process operation fails and the consumer seeks back to the failed offset, the same
 * record is delivered again. A failed delivery stays pending here until a later process operation
 * for it succeeds, so that {@code messaging.client.consumed.messages} does not count the redelivery
 * as a newly consumed message.
 *
 * <p>One tracker belongs to one consumer and is stable for the lifetime of that consumer, so that
 * two consumers reading the same offset are tracked independently. It does not reference the
 * consumer, so attaching it to consumer records does not keep the consumer alive.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class DeliveryTracker {

  private static final int MAX_PENDING_FAILED_DELIVERIES = 1024;

  // bounded so that a consumer that never recovers cannot grow this without limit
  private final Cache<String, Object> pendingFailedDeliveries =
      Cache.bounded(MAX_PENDING_FAILED_DELIVERIES);

  synchronized boolean isPendingFailed(String deliveryKey) {
    return pendingFailedDeliveries.get(deliveryKey) != null;
  }

  synchronized DeliveryState start(List<String> deliveryKeys) {
    Object[] pendingFailures = new Object[deliveryKeys.size()];
    for (int i = 0; i < deliveryKeys.size(); i++) {
      pendingFailures[i] = pendingFailedDeliveries.get(deliveryKeys.get(i));
    }
    return new DeliveryState(this, deliveryKeys, pendingFailures);
  }

  private synchronized void end(DeliveryState state, boolean successful) {
    for (int i = 0; i < state.deliveryKeys.size(); i++) {
      String deliveryKey = state.deliveryKeys.get(i);
      if (!successful) {
        pendingFailedDeliveries.put(deliveryKey, new Object());
      } else if (state.pendingFailures[i] != null
          && state.pendingFailures[i] == pendingFailedDeliveries.get(deliveryKey)) {
        pendingFailedDeliveries.remove(deliveryKey);
      }
    }
  }

  static final class DeliveryState {
    private final DeliveryTracker deliveryTracker;
    private final List<String> deliveryKeys;
    private final Object[] pendingFailures;

    private DeliveryState(
        DeliveryTracker deliveryTracker, List<String> deliveryKeys, Object[] pendingFailures) {
      this.deliveryTracker = deliveryTracker;
      this.deliveryKeys = deliveryKeys;
      this.pendingFailures = pendingFailures;
    }

    boolean wasPendingFailed(int index) {
      return pendingFailures[index] != null;
    }

    void end(boolean successful) {
      deliveryTracker.end(this, successful);
    }
  }
}
