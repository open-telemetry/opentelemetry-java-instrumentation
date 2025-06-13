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
    return create(
        records,
        KafkaUtil.getConsumerGroup(consumer),
        KafkaUtil.getClientId(consumer),
        KafkaUtil.getBootstrapServers(consumer));
  }

  public static KafkaReceiveRequest create(
      KafkaConsumerContext consumerContext, ConsumerRecords<?, ?> records) {
    String consumerGroup = consumerContext != null ? consumerContext.getConsumerGroup() : null;
    String clientId = consumerContext != null ? consumerContext.getClientId() : null;
    String bootstrapServers =
        consumerContext != null ? consumerContext.getBootstrapServers() : null;
    return create(records, consumerGroup, clientId, bootstrapServers);
  }

  public static KafkaReceiveRequest create(
      ConsumerRecords<?, ?> records,
      String consumerGroup,
      String clientId,
      String bootstrapServers) {
    return new KafkaReceiveRequest(records, consumerGroup, clientId, bootstrapServers);
  }

  private KafkaReceiveRequest(
      ConsumerRecords<?, ?> records,
      String consumerGroup,
      String clientId,
      String bootstrapServers) {
    super(consumerGroup, clientId, bootstrapServers);
    this.records = records;
  }

  public ConsumerRecords<?, ?> getRecords() {
    return records;
  }
}
