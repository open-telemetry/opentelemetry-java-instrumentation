/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class KafkaConsumerAdditionalAttributesExtractor
    extends AttributesExtractor<ConsumerRecord<?, ?>, Void> {
  @Override
  protected void onStart(AttributesBuilder attributes, ConsumerRecord<?, ?> consumerRecord) {
    set(
        attributes,
        SemanticAttributes.MESSAGING_KAFKA_PARTITION,
        (long) consumerRecord.partition());
    if (consumerRecord.value() == null) {
      set(attributes, SemanticAttributes.MESSAGING_KAFKA_TOMBSTONE, true);
    }
  }

  @Override
  protected void onEnd(
      AttributesBuilder attributes,
      ConsumerRecord<?, ?> consumerRecord,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
