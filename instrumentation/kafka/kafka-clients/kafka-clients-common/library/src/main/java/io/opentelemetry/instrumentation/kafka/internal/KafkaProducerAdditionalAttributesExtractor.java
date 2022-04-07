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
import org.apache.kafka.clients.producer.ProducerRecord;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class KafkaProducerAdditionalAttributesExtractor
    implements AttributesExtractor<ProducerRecord<?, ?>, Void> {
  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, ProducerRecord<?, ?> producerRecord) {
    Integer partition = producerRecord.partition();
    if (partition != null) {
      attributes.put(SemanticAttributes.MESSAGING_KAFKA_PARTITION, partition.longValue());
    }
    if (producerRecord.value() == null) {
      attributes.put(SemanticAttributes.MESSAGING_KAFKA_TOMBSTONE, true);
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      ProducerRecord<?, ?> producerRecord,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
