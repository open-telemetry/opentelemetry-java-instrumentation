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
  private boolean destinationVaries;
  private boolean partitionVaries;

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
  void putCommonAttributes(AttributesBuilder attributes) {
    if (!initialized) {
      return;
    }
    if (!partitionBelongsOnLinks()) {
      attributes.put(MESSAGING_DESTINATION_PARTITION_ID, commonPartition);
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
    // the offset and the message key are only recommended on spans that describe an operation on a
    // single message, and put() is a batching operation whose span always reports
    // messaging.batch.message_count, so they stay on the links even when the batch happens to
    // carry a single record
    if (partition != null) {
      attributes.put(MESSAGING_KAFKA_OFFSET, record.kafkaOffset());
    }
    attributes.put(MESSAGING_KAFKA_MESSAGE_KEY, serializeKey(record.key()));
    return attributes.build();
  }

  // a partition id only identifies a partition within a destination, so it has to follow the
  // destination name down to the links when the batch spans more than one topic
  private boolean partitionBelongsOnLinks() {
    return destinationVaries || partitionVaries;
  }

  private void accept(SinkRecord record) {
    String destination = record.topic();
    String partition = partitionId(record);
    if (!initialized) {
      initialized = true;
      commonDestination = destination;
      commonPartition = partition;
      return;
    }
    destinationVaries |= !Objects.equals(commonDestination, destination);
    partitionVaries |= !Objects.equals(commonPartition, partition);
  }

  @Nullable
  private static String partitionId(SinkRecord record) {
    Integer partition = record.kafkaPartition();
    return partition == null ? null : partition.toString();
  }
}
