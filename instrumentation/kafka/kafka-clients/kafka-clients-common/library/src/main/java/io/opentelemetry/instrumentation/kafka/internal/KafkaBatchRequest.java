/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka.internal;

import org.apache.kafka.clients.consumer.ConsumerRecords;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class KafkaBatchRequest {
  private final ConsumerRecords<?, ?> consumerRecords;
  private final String consumerGroup;
  private final String clientId;

  public KafkaBatchRequest(
      ConsumerRecords<?, ?> consumerRecords, String consumerGroup, String clientId) {
    this.consumerRecords = consumerRecords;
    this.consumerGroup = consumerGroup;
    this.clientId = clientId;
  }

  public ConsumerRecords<?, ?> getConsumerRecords() {
    return consumerRecords;
  }

  public String getConsumerGroup() {
    return consumerGroup;
  }

  public String getClientId() {
    return clientId;
  }

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
