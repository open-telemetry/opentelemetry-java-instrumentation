/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka.internal;

import org.apache.kafka.clients.producer.ProducerRecord;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class KafkaProducerRequest {
  private final ProducerRecord<?, ?> producerRecord;
  private final String clientId;

  public KafkaProducerRequest(ProducerRecord<?, ?> producerRecord, String clientId) {
    this.producerRecord = producerRecord;
    this.clientId = clientId;
  }

  public ProducerRecord<?, ?> getProducerRecord() {
    return producerRecord;
  }

  public String getClientId() {
    return clientId;
  }
}
