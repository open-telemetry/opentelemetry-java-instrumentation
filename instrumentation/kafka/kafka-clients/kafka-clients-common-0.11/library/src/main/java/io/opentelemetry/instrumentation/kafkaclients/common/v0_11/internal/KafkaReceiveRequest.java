/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import java.util.List;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class KafkaReceiveRequest extends AbstractKafkaConsumerRequest {

  private final ConsumerRecords<?, ?> records;
  private final List<ConsumerRecord<?, ?>> recordList;
  @Nullable private KafkaBatchRecordAttributes batchRecordAttributes;

  public static KafkaReceiveRequest create(
      ConsumerRecords<?, ?> records, @Nullable Consumer<?, ?> consumer) {
    return create(records, KafkaUtil.getConsumerGroup(consumer), KafkaUtil.getClientId(consumer));
  }

  public static KafkaReceiveRequest create(
      KafkaConsumerContext consumerContext, ConsumerRecords<?, ?> records) {
    return create(records, consumerContext.getConsumerGroup(), consumerContext.getClientId());
  }

  public static KafkaReceiveRequest create(
      ConsumerRecords<?, ?> records, @Nullable String consumerGroup, @Nullable String clientId) {
    return new KafkaReceiveRequest(records, consumerGroup, clientId);
  }

  private KafkaReceiveRequest(
      ConsumerRecords<?, ?> records, @Nullable String consumerGroup, @Nullable String clientId) {
    super(consumerGroup, clientId);
    this.records = records;
    this.recordList = KafkaConsumerContextUtil.getRecords(records);
  }

  public ConsumerRecords<?, ?> getRecords() {
    return records;
  }

  List<ConsumerRecord<?, ?>> getRecordList() {
    return recordList;
  }

  // both the attributes extractor and the span links extractor need this, and they are always
  // called on the same thread while the span is being started
  KafkaBatchRecordAttributes getBatchRecordAttributes() {
    if (batchRecordAttributes == null) {
      batchRecordAttributes = KafkaBatchRecordAttributes.create(this);
    }
    return batchRecordAttributes;
  }
}
