/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka.internal;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public final class KafkaConsumerAdditionalAttributesExtractor
    implements AttributesExtractor<ConsumerRecord<?, ?>, Void> {
  @Override
  public void onStart(AttributesBuilder attributes, ConsumerRecord<?, ?> consumerRecord) {
    set(
        attributes,
        SemanticAttributes.MESSAGING_KAFKA_PARTITION,
        (long) consumerRecord.partition());
    if (consumerRecord.value() == null) {
      set(attributes, SemanticAttributes.MESSAGING_KAFKA_TOMBSTONE, true);
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      ConsumerRecord<?, ?> consumerRecord,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
