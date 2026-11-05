/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.ConsumerRecord;

final class KafkaConsumerAttributesExtractor
    implements AttributesExtractor<KafkaProcessRequest, Void> {

  // copied from MessagingIncubatingAttributes
  private static final AttributeKey<String> MESSAGING_DESTINATION_PARTITION_ID =
      AttributeKey.stringKey("messaging.destination.partition.id");
  private static final AttributeKey<String> MESSAGING_CONSUMER_GROUP_NAME =
      AttributeKey.stringKey("messaging.consumer.group.name");
  // copied from KafkaIncubatingAttributes
  private static final AttributeKey<String> KAFKA_MESSAGE_KEY =
      AttributeKey.stringKey("kafka.message.key");
  private static final AttributeKey<Long> KAFKA_OFFSET = AttributeKey.longKey("kafka.offset");
  private static final AttributeKey<Boolean> KAFKA_MESSAGE_TOMBSTONE =
      AttributeKey.booleanKey("kafka.message.tombstone");

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, KafkaProcessRequest request) {

    ConsumerRecord<?, ?> record = request.getRecord();

    attributes.put(MESSAGING_DESTINATION_PARTITION_ID, String.valueOf(record.partition()));
    attributes.put(KAFKA_OFFSET, record.offset());
    attributes.put(KAFKA_MESSAGE_KEY, KafkaUtil.serializeKey(record.key()));
    if (record.value() == null) {
      attributes.put(KAFKA_MESSAGE_TOMBSTONE, true);
    }

    attributes.put(MESSAGING_CONSUMER_GROUP_NAME, request.getConsumerGroup());
    attributes.put(KafkaClusterId.ATTRIBUTE_KEY, request.getClusterId());
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      KafkaProcessRequest request,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
