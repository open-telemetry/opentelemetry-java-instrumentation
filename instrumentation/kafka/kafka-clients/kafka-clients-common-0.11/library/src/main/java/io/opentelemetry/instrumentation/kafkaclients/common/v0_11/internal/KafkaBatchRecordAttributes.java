/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;

final class KafkaBatchRecordAttributes {

  // copied from MessagingIncubatingAttributes
  private static final AttributeKey<String> MESSAGING_DESTINATION_NAME =
      AttributeKey.stringKey("messaging.destination.name");
  private static final AttributeKey<String> MESSAGING_DESTINATION_PARTITION_ID =
      AttributeKey.stringKey("messaging.destination.partition.id");
  private static final AttributeKey<String> MESSAGING_KAFKA_MESSAGE_KEY =
      AttributeKey.stringKey("messaging.kafka.message.key");
  private static final AttributeKey<Long> MESSAGING_KAFKA_OFFSET =
      AttributeKey.longKey("messaging.kafka.offset");

  private boolean initialized;
  @Nullable private String commonPartition;
  @Nullable private Long commonOffset;
  @Nullable private String commonKey;
  private boolean destinationVaries;
  private boolean partitionVaries;
  private boolean offsetVaries;
  private boolean keyVaries;

  static KafkaBatchRecordAttributes create(ConsumerRecords<?, ?> records) {
    KafkaBatchRecordAttributes attributes = new KafkaBatchRecordAttributes();
    attributes.destinationVaries = destinationVaries(records);
    for (ConsumerRecord<?, ?> record : records) {
      attributes.accept(record);
    }
    return attributes;
  }

  // this has to be derived from the same partition set that
  // KafkaReceiveAttributesGetter#getDestination uses, and not from the records themselves,
  // otherwise
  // a batch that carries an empty partition of a second topic would leave the batch span without a
  // destination name while still keeping the partition id and the offset on it
  private static boolean destinationVaries(ConsumerRecords<?, ?> records) {
    Set<String> destinations = new HashSet<>();
    for (TopicPartition partition : records.partitions()) {
      destinations.add(partition.topic());
    }
    return destinations.size() != 1;
  }

  private KafkaBatchRecordAttributes() {}

  // the common destination is not emitted here, MessagingAttributesExtractor already emits it via
  // KafkaReceiveAttributesGetter#getDestination, which derives it from the same partition set
  //
  // these attributes reach the messaging metrics as well as the span, because the receive operation
  // is recorded even when no receive span is created. only the partition id survives the metric
  // attributes advice in MessagingMetricsAdvice; the offset and the message key are deliberately
  // absent from that list and must stay that way, since either would be unbounded as a metric
  // dimension
  void putCommonAttributes(AttributesBuilder attributes) {
    if (!initialized) {
      return;
    }
    if (!partitionBelongsOnLinks()) {
      attributes.put(MESSAGING_DESTINATION_PARTITION_ID, commonPartition);
    }
    if (!offsetBelongsOnLinks()) {
      attributes.put(MESSAGING_KAFKA_OFFSET, commonOffset);
    }
    if (!keyVaries) {
      attributes.put(MESSAGING_KAFKA_MESSAGE_KEY, commonKey);
    }
  }

  Attributes getLinkAttributes(ConsumerRecord<?, ?> record) {
    AttributesBuilder attributes = Attributes.builder();
    if (destinationVaries) {
      attributes.put(MESSAGING_DESTINATION_NAME, record.topic());
    }
    if (partitionBelongsOnLinks()) {
      attributes.put(MESSAGING_DESTINATION_PARTITION_ID, Integer.toString(record.partition()));
    }
    if (offsetBelongsOnLinks()) {
      attributes.put(MESSAGING_KAFKA_OFFSET, record.offset());
    }
    if (keyVaries) {
      attributes.put(MESSAGING_KAFKA_MESSAGE_KEY, KafkaUtil.serializeKey(record.key()));
    }
    return attributes.build();
  }

  // a partition id only identifies a partition within a destination, so it has to follow the
  // destination name down to the links when the batch spans more than one topic
  private boolean partitionBelongsOnLinks() {
    return destinationVaries || partitionVaries;
  }

  // an offset only identifies a record within a destination and partition
  private boolean offsetBelongsOnLinks() {
    return partitionBelongsOnLinks() || offsetVaries;
  }

  private void accept(ConsumerRecord<?, ?> record) {
    String partition = Integer.toString(record.partition());
    Long offset = record.offset();
    String key = KafkaUtil.serializeKey(record.key());
    if (!initialized) {
      initialized = true;
      commonPartition = partition;
      commonOffset = offset;
      commonKey = key;
      return;
    }
    partitionVaries |= !Objects.equals(commonPartition, partition);
    offsetVaries |= !Objects.equals(commonOffset, offset);
    keyVaries |= !Objects.equals(commonKey, key);
  }
}
