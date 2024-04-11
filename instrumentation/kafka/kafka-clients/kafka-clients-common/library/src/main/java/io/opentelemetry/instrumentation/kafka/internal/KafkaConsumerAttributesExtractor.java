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
import org.apache.kafka.clients.consumer.ConsumerRecord;

final class KafkaConsumerAttributesExtractor
    implements AttributesExtractor<KafkaProcessRequest, Void> {

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, KafkaProcessRequest request) {

    ConsumerRecord<?, ?> record = request.getRecord();

    attributes.put(
        MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID,
        String.valueOf(record.partition()));
    attributes.put(MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET, record.offset());

    Object key = record.key();
    if (key != null && canSerialize(key.getClass())) {
      attributes.put(MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_KEY, key.toString());
    }
    if (record.value() == null) {
      attributes.put(MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_TOMBSTONE, true);
    }

    String consumerGroup = request.getConsumerGroup();
    if (consumerGroup != null) {
      attributes.put(MessagingIncubatingAttributes.MESSAGING_KAFKA_CONSUMER_GROUP, consumerGroup);
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
      KafkaProcessRequest request,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
