/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;

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
  private static final AttributeKey<String> MESSAGING_KAFKA_CONSUMER_GROUP =
      AttributeKey.stringKey("messaging.kafka.consumer.group");
  private static final AttributeKey<String> MESSAGING_CONSUMER_GROUP_NAME =
      AttributeKey.stringKey("messaging.consumer.group.name");
  private static final AttributeKey<String> MESSAGING_KAFKA_MESSAGE_KEY =
      AttributeKey.stringKey("messaging.kafka.message.key");
  private static final AttributeKey<Long> MESSAGING_KAFKA_MESSAGE_OFFSET =
      AttributeKey.longKey("messaging.kafka.message.offset");
  private static final AttributeKey<Long> MESSAGING_KAFKA_OFFSET =
      AttributeKey.longKey("messaging.kafka.offset");
  private static final AttributeKey<Boolean> MESSAGING_KAFKA_MESSAGE_TOMBSTONE =
      AttributeKey.booleanKey("messaging.kafka.message.tombstone");

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, KafkaProcessRequest request) {

    ConsumerRecord<?, ?> record = request.getRecord();

    attributes.put(MESSAGING_DESTINATION_PARTITION_ID, String.valueOf(record.partition()));
    if (emitStableMessagingSemconv()) {
      attributes.put(MESSAGING_KAFKA_OFFSET, record.offset());
    }
    if (emitOldMessagingSemconv()) {
      attributes.put(MESSAGING_KAFKA_MESSAGE_OFFSET, record.offset());
    }
    attributes.put(MESSAGING_KAFKA_MESSAGE_KEY, KafkaUtil.serializeKey(record.key()));
    if (record.value() == null) {
      attributes.put(MESSAGING_KAFKA_MESSAGE_TOMBSTONE, true);
    }

    if (emitOldMessagingSemconv()) {
      attributes.put(MESSAGING_KAFKA_CONSUMER_GROUP, request.getConsumerGroup());
    }
    if (emitStableMessagingSemconv()) {
      attributes.put(MESSAGING_CONSUMER_GROUP_NAME, request.getConsumerGroup());
    }
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
