/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import java.util.Objects;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.ConsumerRecord;

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
  @Nullable private String commonDestination;
  @Nullable private String commonPartition;
  @Nullable private Long commonOffset;
  @Nullable private String commonKey;
  private boolean destinationVaries;
  private boolean partitionVaries;
  private boolean offsetVaries;
  private boolean keyVaries;

  static KafkaBatchRecordAttributes create(Iterable<? extends ConsumerRecord<?, ?>> records) {
    KafkaBatchRecordAttributes attributes = new KafkaBatchRecordAttributes();
    for (ConsumerRecord<?, ?> record : records) {
      attributes.accept(record);
    }
    return attributes;
  }

  void putCommonAttributes(AttributesBuilder attributes) {
    if (!initialized) {
      return;
    }
    if (!destinationVaries) {
      attributes.put(MESSAGING_DESTINATION_NAME, commonDestination);
    }
    if (!partitionVaries) {
      attributes.put(MESSAGING_DESTINATION_PARTITION_ID, commonPartition);
    }
    if (!offsetVaries) {
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
    if (partitionVaries) {
      attributes.put(MESSAGING_DESTINATION_PARTITION_ID, Integer.toString(record.partition()));
    }
    if (offsetVaries) {
      attributes.put(MESSAGING_KAFKA_OFFSET, record.offset());
    }
    if (keyVaries) {
      attributes.put(MESSAGING_KAFKA_MESSAGE_KEY, KafkaUtil.serializeKey(record.key()));
    }
    return attributes.build();
  }

  private void accept(ConsumerRecord<?, ?> record) {
    String destination = record.topic();
    String partition = Integer.toString(record.partition());
    Long offset = record.offset();
    String key = KafkaUtil.serializeKey(record.key());
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

  private KafkaBatchRecordAttributes() {}
}
