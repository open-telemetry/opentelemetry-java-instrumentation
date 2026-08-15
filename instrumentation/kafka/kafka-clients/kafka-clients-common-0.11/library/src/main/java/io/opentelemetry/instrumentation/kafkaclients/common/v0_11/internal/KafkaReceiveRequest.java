/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class KafkaReceiveRequest extends AbstractKafkaConsumerRequest {

  private final ConsumerRecords<?, ?> records;
  @Nullable private KafkaBatchRecordAttributes batchRecordAttributes;

  public static KafkaReceiveRequest create(
      ConsumerRecords<?, ?> records, @Nullable Consumer<?, ?> consumer) {
    return create(
        records,
        KafkaUtil.getConsumerGroup(consumer),
        KafkaUtil.getClientId(consumer),
        KafkaUtil.getDeliveryIdentity(consumer));
  }

  public static KafkaReceiveRequest create(
      KafkaConsumerContext consumerContext, ConsumerRecords<?, ?> records) {
    return create(
        records,
        consumerContext.getConsumerGroup(),
        consumerContext.getClientId(),
        consumerContext.getDeliveryIdentity());
  }

  public static KafkaReceiveRequest create(
      ConsumerRecords<?, ?> records, @Nullable String consumerGroup, @Nullable String clientId) {
    return create(records, consumerGroup, clientId, null);
  }

  public static KafkaReceiveRequest create(
      ConsumerRecords<?, ?> records,
      @Nullable String consumerGroup,
      @Nullable String clientId,
      @Nullable Object deliveryIdentity) {
    return new KafkaReceiveRequest(records, consumerGroup, clientId, deliveryIdentity);
  }

  private KafkaReceiveRequest(
      ConsumerRecords<?, ?> records,
      @Nullable String consumerGroup,
      @Nullable String clientId,
      @Nullable Object deliveryIdentity) {
    super(consumerGroup, clientId, deliveryIdentity);
    this.records = records;
  }

  public ConsumerRecords<?, ?> getRecords() {
    return records;
  }

  // both the attributes extractor and the span links extractor need this, and they are always
  // called on the same thread while the span is being started
  KafkaBatchRecordAttributes getBatchRecordAttributes() {
    if (batchRecordAttributes == null) {
      batchRecordAttributes = KafkaBatchRecordAttributes.create(records);
    }
    return batchRecordAttributes;
  }
}
