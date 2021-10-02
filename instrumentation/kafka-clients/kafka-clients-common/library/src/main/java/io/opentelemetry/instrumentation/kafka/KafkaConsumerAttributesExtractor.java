/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka;

import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class KafkaConsumerAttributesExtractor
    extends MessagingAttributesExtractor<ConsumerRecord<?, ?>, Void> {

  private final MessageOperation messageOperation;

  public KafkaConsumerAttributesExtractor(MessageOperation messageOperation) {
    this.messageOperation = messageOperation;
  }

  @Override
  public MessageOperation operation() {
    return messageOperation;
  }

  @Override
  protected String system(ConsumerRecord<?, ?> consumerRecord) {
    return "kafka";
  }

  @Override
  protected String destinationKind(ConsumerRecord<?, ?> consumerRecord) {
    return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
  }

  @Override
  protected String destination(ConsumerRecord<?, ?> consumerRecord) {
    return consumerRecord.topic();
  }

  @Override
  protected boolean temporaryDestination(ConsumerRecord<?, ?> consumerRecord) {
    return false;
  }

  @Override
  protected @Nullable String protocol(ConsumerRecord<?, ?> consumerRecord) {
    return null;
  }

  @Override
  protected @Nullable String protocolVersion(ConsumerRecord<?, ?> consumerRecord) {
    return null;
  }

  @Override
  protected @Nullable String url(ConsumerRecord<?, ?> consumerRecord) {
    return null;
  }

  @Override
  protected @Nullable String conversationId(ConsumerRecord<?, ?> consumerRecord) {
    return null;
  }

  @Override
  protected Long messagePayloadSize(ConsumerRecord<?, ?> consumerRecord) {
    return (long) consumerRecord.serializedValueSize();
  }

  @Override
  protected @Nullable Long messagePayloadCompressedSize(ConsumerRecord<?, ?> consumerRecord) {
    return null;
  }

  @Override
  protected @Nullable String messageId(ConsumerRecord<?, ?> consumerRecord, @Nullable Void unused) {
    return null;
  }
}
