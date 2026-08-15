/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import javax.annotation.Nullable;

abstract class AbstractKafkaConsumerRequest {

  @Nullable private final String consumerGroup;
  @Nullable private final String clientId;
  @Nullable private final Object deliveryIdentity;

  AbstractKafkaConsumerRequest(
      @Nullable String consumerGroup,
      @Nullable String clientId,
      @Nullable Object deliveryIdentity) {
    this.consumerGroup = consumerGroup;
    this.clientId = clientId;
    this.deliveryIdentity = deliveryIdentity;
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
   * Returns the object that identifies the source of this delivery, or {@code null} if it is
   * unknown. This must be stable across redeliveries of the same records, e.g. a token bound to the
   * lifetime of the {@code Consumer}, so that a retried delivery can be recognized instead of being
   * counted again.
   */
  @Nullable
  Object getDeliveryIdentity() {
    return deliveryIdentity;
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
