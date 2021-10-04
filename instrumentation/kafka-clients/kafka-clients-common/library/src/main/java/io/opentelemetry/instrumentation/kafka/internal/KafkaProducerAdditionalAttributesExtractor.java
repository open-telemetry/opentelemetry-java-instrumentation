/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka.internal;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class KafkaProducerAdditionalAttributesExtractor
    extends AttributesExtractor<ProducerRecord<?, ?>, Void> {
  @Override
  protected void onStart(AttributesBuilder attributes, ProducerRecord<?, ?> producerRecord) {
    Integer partition = producerRecord.partition();
    if (partition != null) {
      set(attributes, SemanticAttributes.MESSAGING_KAFKA_PARTITION, partition.longValue());
    }
    if (producerRecord.value() == null) {
      set(attributes, SemanticAttributes.MESSAGING_KAFKA_TOMBSTONE, true);
    }
  }

  @Override
  protected void onEnd(
      AttributesBuilder attributes,
      ProducerRecord<?, ?> producerRecord,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
