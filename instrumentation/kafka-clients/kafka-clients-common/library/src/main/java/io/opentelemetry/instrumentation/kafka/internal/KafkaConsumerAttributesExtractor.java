/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka.internal;

import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.ConsumerRecord;

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
  @Nullable
  protected String protocol(ConsumerRecord<?, ?> consumerRecord) {
    return null;
  }

  @Override
  @Nullable
  protected String protocolVersion(ConsumerRecord<?, ?> consumerRecord) {
    return null;
  }

  @Override
  @Nullable
  protected String url(ConsumerRecord<?, ?> consumerRecord) {
    return null;
  }

  @Override
  @Nullable
  protected String conversationId(ConsumerRecord<?, ?> consumerRecord) {
    return null;
  }

  @Override
  protected Long messagePayloadSize(ConsumerRecord<?, ?> consumerRecord) {
    return (long) consumerRecord.serializedValueSize();
  }

  @Override
  @Nullable
  protected Long messagePayloadCompressedSize(ConsumerRecord<?, ?> consumerRecord) {
    return null;
  }

  @Override
  @Nullable
  protected String messageId(ConsumerRecord<?, ?> consumerRecord, @Nullable Void unused) {
    return null;
  }
}
