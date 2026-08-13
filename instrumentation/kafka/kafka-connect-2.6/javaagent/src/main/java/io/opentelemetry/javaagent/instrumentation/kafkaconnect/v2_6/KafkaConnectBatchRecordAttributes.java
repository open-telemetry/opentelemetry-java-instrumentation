/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaconnect.v2_6;

import static io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaUtil.serializeKey;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_KEY;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_OFFSET;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import java.util.Objects;
import javax.annotation.Nullable;
import org.apache.kafka.connect.sink.SinkRecord;

final class KafkaConnectBatchRecordAttributes {

  private boolean initialized;
  @Nullable private String commonDestination;
  @Nullable private String commonPartition;
  @Nullable private Long commonOffset;
  @Nullable private String commonKey;
  private boolean destinationVaries;
  private boolean partitionVaries;
  private boolean offsetVaries;
  private boolean keyVaries;

  static KafkaConnectBatchRecordAttributes create(Iterable<SinkRecord> records) {
    KafkaConnectBatchRecordAttributes attributes = new KafkaConnectBatchRecordAttributes();
    for (SinkRecord record : records) {
      attributes.accept(record);
    }
    return attributes;
  }

  private KafkaConnectBatchRecordAttributes() {}

  // the common destination is not emitted here, MessagingAttributesExtractor already emits it via
  // KafkaConnectAttributesGetter#getDestination, which returns the topic under the same condition
  //
  // these attributes reach the messaging metrics as well as the span. only the partition id
  // survives the metric attributes advice in MessagingMetricsAdvice; the offset and the message key
  // are deliberately absent from that list and must stay that way, since either would be unbounded
  // as a metric dimension
  void putCommonAttributes(AttributesBuilder attributes) {
    if (!initialized) {
      return;
    }
    if (!partitionBelongsOnLinks()) {
      attributes.put(MESSAGING_DESTINATION_PARTITION_ID, commonPartition);
    }
    if (!offsetBelongsOnLinks() && commonPartition != null) {
      attributes.put(MESSAGING_KAFKA_OFFSET, commonOffset);
    }
    if (!keyVaries) {
      attributes.put(MESSAGING_KAFKA_MESSAGE_KEY, commonKey);
    }
  }

  Attributes getLinkAttributes(SinkRecord record) {
    AttributesBuilder attributes = Attributes.builder();
    String partition = partitionId(record);
    if (destinationVaries) {
      attributes.put(MESSAGING_DESTINATION_NAME, record.topic());
    }
    if (partitionBelongsOnLinks()) {
      attributes.put(MESSAGING_DESTINATION_PARTITION_ID, partition);
    }
    if (offsetBelongsOnLinks() && partition != null) {
      attributes.put(MESSAGING_KAFKA_OFFSET, record.kafkaOffset());
    }
    if (keyVaries) {
      attributes.put(MESSAGING_KAFKA_MESSAGE_KEY, serializeKey(record.key()));
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

  private void accept(SinkRecord record) {
    String destination = record.topic();
    String partition = partitionId(record);
    Long offset = record.kafkaOffset();
    String key = serializeKey(record.key());
    if (!initialized) {
      initialized = true;
      commonDestination = destination;
      commonPartition = partition;
      commonOffset = offset;
      commonKey = key;
      return;
    }
    destinationVaries |= !Objects.equals(commonDestination, destination);
    partitionVaries |= !Objects.equals(commonPartition, partition);
    offsetVaries |= !Objects.equals(commonOffset, offset);
    keyVaries |= !Objects.equals(commonKey, key);
  }

  @Nullable
  private static String partitionId(SinkRecord record) {
    Integer partition = record.kafkaPartition();
    return partition == null ? null : partition.toString();
  }
}
