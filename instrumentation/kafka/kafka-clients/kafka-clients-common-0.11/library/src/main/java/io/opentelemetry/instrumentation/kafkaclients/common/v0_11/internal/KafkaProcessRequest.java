/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import java.util.List;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class KafkaProcessRequest extends AbstractKafkaConsumerRequest {

  private final ConsumerRecord<?, ?> record;

  public static KafkaProcessRequest create(ConsumerRecord<?, ?> record, Consumer<?, ?> consumer) {
    return create(
        record,
        KafkaUtil.getConsumerGroup(consumer),
        KafkaUtil.getClientId(consumer),
        KafkaUtil.getBootstrapServers(consumer));
  }

  public static KafkaProcessRequest create(
      KafkaConsumerContext consumerContext, ConsumerRecord<?, ?> record) {
    String consumerGroup = consumerContext != null ? consumerContext.getConsumerGroup() : null;
    String clientId = consumerContext != null ? consumerContext.getClientId() : null;
    List<String> bootstrapServers =
        consumerContext != null ? consumerContext.getBootstrapServers() : null;
    return create(record, consumerGroup, clientId, bootstrapServers);
  }

  public static KafkaProcessRequest create(
      ConsumerRecord<?, ?> record,
      String consumerGroup,
      String clientId,
      List<String> bootstrapServers) {
    return new KafkaProcessRequest(record, consumerGroup, clientId, bootstrapServers);
  }

  public KafkaProcessRequest(
      ConsumerRecord<?, ?> record,
      String consumerGroup,
      String clientId,
      List<String> bootstrapServers) {
    super(consumerGroup, clientId, bootstrapServers);
    this.record = record;
  }

  public ConsumerRecord<?, ?> getRecord() {
    return record;
  }
}
