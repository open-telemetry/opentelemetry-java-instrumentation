/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import io.opentelemetry.instrumentation.api.internal.cache.Cache;

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
  private final Cache<String, Boolean> pendingFailedDeliveries =
      Cache.bounded(MAX_PENDING_FAILED_DELIVERIES);

  boolean isPendingFailed(String deliveryKey) {
    return pendingFailedDeliveries.get(deliveryKey) != null;
  }

  void setPendingFailed(String deliveryKey, boolean pendingFailed) {
    if (pendingFailed) {
      pendingFailedDeliveries.put(deliveryKey, true);
    } else {
      pendingFailedDeliveries.remove(deliveryKey);
    }
  }
}
