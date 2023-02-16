/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka.internal;

import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class KafkaConsumerRequest {
  private final ConsumerRecord<?, ?> consumerRecord;
  private final String consumerGroup;
  private final String clientId;

  public KafkaConsumerRequest(
      ConsumerRecord<?, ?> consumerRecord, String consumerGroup, String clientId) {
    this.consumerRecord = consumerRecord;
    this.consumerGroup = consumerGroup;
    this.clientId = clientId;
  }

  public ConsumerRecord<?, ?> getConsumerRecord() {
    return consumerRecord;
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
