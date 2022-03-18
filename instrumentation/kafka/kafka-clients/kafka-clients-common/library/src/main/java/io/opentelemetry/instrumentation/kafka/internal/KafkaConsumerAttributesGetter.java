/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka.internal;

import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public enum KafkaConsumerAttributesGetter
    implements MessagingAttributesGetter<ConsumerRecord<?, ?>, Void> {
  INSTANCE;

  @Override
  public String system(ConsumerRecord<?, ?> consumerRecord) {
    return "kafka";
  }

  @Override
  public String destinationKind(ConsumerRecord<?, ?> consumerRecord) {
    return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
  }

  @Override
  public String destination(ConsumerRecord<?, ?> consumerRecord) {
    return consumerRecord.topic();
  }

  @Override
  public boolean temporaryDestination(ConsumerRecord<?, ?> consumerRecord) {
    return false;
  }

  @Override
  @Nullable
  public String protocol(ConsumerRecord<?, ?> consumerRecord) {
    return null;
  }

  @Override
  @Nullable
  public String protocolVersion(ConsumerRecord<?, ?> consumerRecord) {
    return null;
  }

  @Override
  @Nullable
  public String url(ConsumerRecord<?, ?> consumerRecord) {
    return null;
  }

  @Override
  @Nullable
  public String conversationId(ConsumerRecord<?, ?> consumerRecord) {
    return null;
  }

  @Override
  public Long messagePayloadSize(ConsumerRecord<?, ?> consumerRecord) {
    return (long) consumerRecord.serializedValueSize();
  }

  @Override
  @Nullable
  public Long messagePayloadCompressedSize(ConsumerRecord<?, ?> consumerRecord) {
    return null;
  }

  @Override
  @Nullable
  public String messageId(ConsumerRecord<?, ?> consumerRecord, @Nullable Void unused) {
    return null;
  }
}
