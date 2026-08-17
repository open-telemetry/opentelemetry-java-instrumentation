/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class KafkaProcessRequest extends AbstractKafkaConsumerRequest {

  private final ConsumerRecord<?, ?> record;

  public static KafkaProcessRequest create(
      ConsumerRecord<?, ?> record, @Nullable Consumer<?, ?> consumer) {
    return create(
        record,
        KafkaUtil.getConsumerGroup(consumer),
        KafkaUtil.getClientId(consumer),
        KafkaUtil.getDeliveryTracker(consumer));
  }

  public static KafkaProcessRequest create(
      KafkaConsumerContext consumerContext, ConsumerRecord<?, ?> record) {
    return create(
        record,
        consumerContext.getConsumerGroup(),
        consumerContext.getClientId(),
        consumerContext.getDeliveryTracker());
  }

  public static KafkaProcessRequest create(
      ConsumerRecord<?, ?> record, @Nullable String consumerGroup, @Nullable String clientId) {
    return create(record, consumerGroup, clientId, null);
  }

  static KafkaProcessRequest create(
      ConsumerRecord<?, ?> record,
      @Nullable String consumerGroup,
      @Nullable String clientId,
      @Nullable DeliveryTracker deliveryTracker) {
    return new KafkaProcessRequest(record, consumerGroup, clientId, deliveryTracker);
  }

  public KafkaProcessRequest(
      ConsumerRecord<?, ?> record, @Nullable String consumerGroup, @Nullable String clientId) {
    this(record, consumerGroup, clientId, null);
  }

  private KafkaProcessRequest(
      ConsumerRecord<?, ?> record,
      @Nullable String consumerGroup,
      @Nullable String clientId,
      @Nullable DeliveryTracker deliveryTracker) {
    super(consumerGroup, clientId, deliveryTracker);
    this.record = record;
  }

  public ConsumerRecord<?, ?> getRecord() {
    return record;
  }
}
