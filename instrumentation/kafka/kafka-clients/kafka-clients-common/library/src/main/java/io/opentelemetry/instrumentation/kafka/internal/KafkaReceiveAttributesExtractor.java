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
import org.apache.kafka.clients.consumer.ConsumerRecords;

enum KafkaReceiveAttributesExtractor
    implements AttributesExtractor<ConsumerAndRecord<ConsumerRecords<?, ?>>, Void> {
  INSTANCE;

  @Override
  public void onStart(
      AttributesBuilder attributes,
      Context parentContext,
      ConsumerAndRecord<ConsumerRecords<?, ?>> consumerAndRecords) {

    String consumerGroup = consumerAndRecords.consumerGroup();
    if (consumerGroup != null) {
      attributes.put(SemanticAttributes.MESSAGING_KAFKA_CONSUMER_GROUP, consumerGroup);
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      ConsumerAndRecord<ConsumerRecords<?, ?>> request,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
