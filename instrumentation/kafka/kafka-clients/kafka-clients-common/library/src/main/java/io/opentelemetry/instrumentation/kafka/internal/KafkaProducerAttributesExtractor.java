/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka.internal;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.nio.ByteBuffer;
import javax.annotation.Nullable;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

final class KafkaProducerAttributesExtractor
    implements AttributesExtractor<ProducerRecord<?, ?>, RecordMetadata> {

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, ProducerRecord<?, ?> record) {

    Object key = record.key();
    if (key != null && canSerialize(key.getClass())) {
      attributes.put(SemanticAttributes.MESSAGING_KAFKA_MESSAGE_KEY, key.toString());
    }
    if (record.value() == null) {
      attributes.put(SemanticAttributes.MESSAGING_KAFKA_MESSAGE_TOMBSTONE, true);
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
      ProducerRecord<?, ?> producerRecord,
      @Nullable RecordMetadata recordMetadata,
      @Nullable Throwable error) {

    if (recordMetadata != null) {
      attributes.put(
          SemanticAttributes.MESSAGING_KAFKA_DESTINATION_PARTITION, recordMetadata.partition());
      attributes.put(SemanticAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET, recordMetadata.offset());
    }
  }
}
