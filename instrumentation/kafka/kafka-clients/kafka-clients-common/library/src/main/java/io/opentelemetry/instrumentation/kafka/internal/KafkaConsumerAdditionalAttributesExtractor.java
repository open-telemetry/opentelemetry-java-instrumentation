/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka.internal;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class KafkaConsumerAdditionalAttributesExtractor
    implements AttributesExtractor<KafkaConsumerRequest, Void> {

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, KafkaConsumerRequest request) {
    ConsumerRecord<?, ?> consumerRecord = request.getConsumerRecord();
    attributes.put(
        SemanticAttributes.MESSAGING_KAFKA_SOURCE_PARTITION, (long) consumerRecord.partition());
    attributes.put(SemanticAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET, consumerRecord.offset());
    if (consumerRecord.value() == null) {
      attributes.put(SemanticAttributes.MESSAGING_KAFKA_MESSAGE_TOMBSTONE, true);
    }

    attributes.put(SemanticAttributes.MESSAGING_KAFKA_CONSUMER_GROUP, request.getConsumerGroup());
    attributes.put(SemanticAttributes.MESSAGING_KAFKA_CLIENT_ID, request.getClientId());
    attributes.put(SemanticAttributes.MESSAGING_CONSUMER_ID, request.getConsumerId());
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      KafkaConsumerRequest request,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
