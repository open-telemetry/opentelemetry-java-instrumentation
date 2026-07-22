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
    return new KafkaProcessRequest(
        record,
        KafkaUtil.getConsumerGroup(consumer),
        KafkaUtil.getClientId(consumer),
        KafkaUtil.getClusterId(consumer));
  }

  public static KafkaProcessRequest create(
      KafkaConsumerContext consumerContext, ConsumerRecord<?, ?> record) {
    return new KafkaProcessRequest(
        record,
        consumerContext.getConsumerGroup(),
        consumerContext.getClientId(),
        consumerContext.getClusterId());
  }

  public static KafkaProcessRequest create(
      ConsumerRecord<?, ?> record, @Nullable String consumerGroup, @Nullable String clientId) {
    return new KafkaProcessRequest(record, consumerGroup, clientId, null);
  }

  public static KafkaProcessRequest create(
      ConsumerRecord<?, ?> record,
      @Nullable String consumerGroup,
      @Nullable String clientId,
      @Nullable String clusterId) {
    return new KafkaProcessRequest(record, consumerGroup, clientId, clusterId);
  }

  public KafkaProcessRequest(
      ConsumerRecord<?, ?> record, @Nullable String consumerGroup, @Nullable String clientId) {
    this(record, consumerGroup, clientId, null);
  }

  public KafkaProcessRequest(
      ConsumerRecord<?, ?> record,
      @Nullable String consumerGroup,
      @Nullable String clientId,
      @Nullable String clusterId) {
    super(consumerGroup, clientId, clusterId);
    this.record = record;
  }

  public ConsumerRecord<?, ?> getRecord() {
    return record;
  }
}
