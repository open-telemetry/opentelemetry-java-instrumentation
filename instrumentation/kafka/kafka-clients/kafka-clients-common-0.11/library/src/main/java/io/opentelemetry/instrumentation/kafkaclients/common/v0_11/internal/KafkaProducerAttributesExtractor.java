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
import org.apache.kafka.clients.producer.RecordMetadata;

final class KafkaProducerAttributesExtractor
    implements AttributesExtractor<KafkaProducerRequest, RecordMetadata> {
  // copied from MessagingIncubatingAttributes
  private static final AttributeKey<String> MESSAGING_DESTINATION_PARTITION_ID =
      AttributeKey.stringKey("messaging.destination.partition.id");
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
      AttributesBuilder attributes, Context parentContext, KafkaProducerRequest request) {

    attributes.put(MESSAGING_KAFKA_MESSAGE_KEY, KafkaUtil.serializeKey(request.getRecord().key()));
    if (request.getRecord().value() == null) {
      attributes.put(MESSAGING_KAFKA_MESSAGE_TOMBSTONE, true);
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
      String resolved = KafkaUtil.getClusterId(request.getProducer());
      if (resolved != null) {
        attributes.put(KafkaClusterId.ATTRIBUTE_KEY, resolved);
      }
    }

    if (recordMetadata != null) {
      attributes.put(
          MESSAGING_DESTINATION_PARTITION_ID, String.valueOf(recordMetadata.partition()));
      if (emitStableMessagingSemconv()) {
        attributes.put(MESSAGING_KAFKA_OFFSET, recordMetadata.offset());
      }
      if (emitOldMessagingSemconv()) {
        attributes.put(MESSAGING_KAFKA_MESSAGE_OFFSET, recordMetadata.offset());
      }
    }
  }
}
