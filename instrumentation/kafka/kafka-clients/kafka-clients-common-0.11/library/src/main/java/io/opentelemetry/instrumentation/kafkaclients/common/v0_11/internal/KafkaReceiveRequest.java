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

  public static KafkaReceiveRequest create(
      ConsumerRecords<?, ?> records, @Nullable Consumer<?, ?> consumer) {
    return new KafkaReceiveRequest(
        records,
        KafkaUtil.getConsumerGroup(consumer),
        KafkaUtil.getClientId(consumer),
        KafkaUtil.getClusterId(consumer));
  }

  public static KafkaReceiveRequest create(
      KafkaConsumerContext consumerContext, ConsumerRecords<?, ?> records) {
    return new KafkaReceiveRequest(
        records,
        consumerContext.getConsumerGroup(),
        consumerContext.getClientId(),
        consumerContext.getClusterId());
  }

  public static KafkaReceiveRequest create(
      ConsumerRecords<?, ?> records, @Nullable String consumerGroup, @Nullable String clientId) {
    return new KafkaReceiveRequest(records, consumerGroup, clientId, null);
  }

  public static KafkaReceiveRequest create(
      ConsumerRecords<?, ?> records,
      @Nullable String consumerGroup,
      @Nullable String clientId,
      @Nullable String clusterId) {
    return new KafkaReceiveRequest(records, consumerGroup, clientId, clusterId);
  }

  private KafkaReceiveRequest(
      ConsumerRecords<?, ?> records,
      @Nullable String consumerGroup,
      @Nullable String clientId,
      @Nullable String clusterId) {
    super(consumerGroup, clientId, clusterId);
    this.records = records;
  }

  public ConsumerRecords<?, ?> getRecords() {
    return records;
  }
}
