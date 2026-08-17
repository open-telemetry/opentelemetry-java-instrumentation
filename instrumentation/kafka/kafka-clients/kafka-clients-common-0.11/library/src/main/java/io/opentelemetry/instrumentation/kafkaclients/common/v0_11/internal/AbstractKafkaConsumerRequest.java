/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import javax.annotation.Nullable;

abstract class AbstractKafkaConsumerRequest {

  @Nullable private final String consumerGroup;
  @Nullable private final String clientId;
  @Nullable private final DeliveryTracker deliveryTracker;

  AbstractKafkaConsumerRequest(
      @Nullable String consumerGroup,
      @Nullable String clientId,
      @Nullable DeliveryTracker deliveryTracker) {
    this.consumerGroup = consumerGroup;
    this.clientId = clientId;
    this.deliveryTracker = deliveryTracker;
  }

  @Nullable
  public String getConsumerGroup() {
    return consumerGroup;
  }

  @Nullable
  public String getClientId() {
    return clientId;
  }

  /**
   * Returns the tracker of the consumer that produced this delivery, or {@code null} if it is
   * unknown. Without a tracker a redelivery cannot be recognized, and is counted again.
   */
  @Nullable
  DeliveryTracker getDeliveryTracker() {
    return deliveryTracker;
  }

  @Nullable
  public String getConsumerId() {
    if (consumerGroup != null) {
      if (clientId != null) {
        return consumerGroup + " - " + clientId;
      }
      return consumerGroup;
    }
    return null;
  }
}
