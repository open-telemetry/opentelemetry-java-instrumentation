/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka.internal;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import java.nio.ByteBuffer;
import javax.annotation.Nullable;
import org.apache.kafka.clients.producer.RecordMetadata;

final class KafkaProducerAttributesExtractor
    implements AttributesExtractor<KafkaProducerRequest, RecordMetadata> {

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, KafkaProducerRequest request) {

    Object key = request.getRecord().key();
    if (key != null && canSerialize(key.getClass())) {
      attributes.put(MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_KEY, key.toString());
    }
    if (request.getRecord().value() == null) {
      attributes.put(MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_TOMBSTONE, true);
    }
  }

  private static boolean canSerialize(Class<?> keyClass) {
    // we make a simple assumption here that we can serialize keys by simply calling toString()
    // and that does not work for byte[] or ByteBuffer
    return !(keyClass.isArray() || keyClass == ByteBuffer.class);
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      KafkaProducerRequest request,
      @Nullable RecordMetadata recordMetadata,
      @Nullable Throwable error) {

    if (recordMetadata != null) {
      attributes.put(
          MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID,
          String.valueOf(recordMetadata.partition()));
      attributes.put(
          MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET, recordMetadata.offset());
    }
  }
}
