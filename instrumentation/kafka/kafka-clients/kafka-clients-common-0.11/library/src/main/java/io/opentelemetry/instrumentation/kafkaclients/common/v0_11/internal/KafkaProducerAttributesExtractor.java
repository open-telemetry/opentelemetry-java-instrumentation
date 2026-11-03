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
import org.apache.kafka.clients.producer.RecordMetadata;

final class KafkaProducerAttributesExtractor
    implements AttributesExtractor<KafkaProducerRequest, RecordMetadata> {
  // copied from MessagingIncubatingAttributes
  private static final AttributeKey<String> MESSAGING_DESTINATION_PARTITION_ID =
      AttributeKey.stringKey("messaging.destination.partition.id");
  // copied from KafkaIncubatingAttributes
  private static final AttributeKey<String> KAFKA_MESSAGE_KEY =
      AttributeKey.stringKey("kafka.message.key");
  private static final AttributeKey<Long> KAFKA_OFFSET = AttributeKey.longKey("kafka.offset");
  private static final AttributeKey<Boolean> KAFKA_MESSAGE_TOMBSTONE =
      AttributeKey.booleanKey("kafka.message.tombstone");

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, KafkaProducerRequest request) {

    attributes.put(KAFKA_MESSAGE_KEY, KafkaUtil.serializeKey(request.getRecord().key()));
    if (request.getRecord().value() == null) {
      attributes.put(KAFKA_MESSAGE_TOMBSTONE, true);
    }
    attributes.put(KafkaClusterId.ATTRIBUTE_KEY, request.getClusterId());
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      KafkaProducerRequest request,
      @Nullable RecordMetadata recordMetadata,
      @Nullable Throwable error) {

    if (request.getClusterId() == null) {
      attributes.put(KafkaClusterId.ATTRIBUTE_KEY, KafkaUtil.getClusterId(request.getProducer()));
    }

    if (recordMetadata != null) {
      attributes.put(
          MESSAGING_DESTINATION_PARTITION_ID, String.valueOf(recordMetadata.partition()));
      attributes.put(KAFKA_OFFSET, recordMetadata.offset());
    }
  }
}
