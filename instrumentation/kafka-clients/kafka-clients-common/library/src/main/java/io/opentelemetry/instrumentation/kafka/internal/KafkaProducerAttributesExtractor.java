/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka.internal;

import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class KafkaProducerAttributesExtractor
    extends MessagingAttributesExtractor<ProducerRecord<?, ?>, Void> {

  @Override
  public MessageOperation operation() {
    return MessageOperation.SEND;
  }

  @Override
  protected String system(ProducerRecord<?, ?> producerRecord) {
    return "kafka";
  }

  @Override
  protected String destinationKind(ProducerRecord<?, ?> producerRecord) {
    return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
  }

  @Override
  protected String destination(ProducerRecord<?, ?> producerRecord) {
    return producerRecord.topic();
  }

  @Override
  protected boolean temporaryDestination(ProducerRecord<?, ?> producerRecord) {
    return false;
  }

  @Override
  protected @Nullable String protocol(ProducerRecord<?, ?> producerRecord) {
    return null;
  }

  @Override
  protected @Nullable String protocolVersion(ProducerRecord<?, ?> producerRecord) {
    return null;
  }

  @Override
  protected @Nullable String url(ProducerRecord<?, ?> producerRecord) {
    return null;
  }

  @Override
  protected @Nullable String conversationId(ProducerRecord<?, ?> producerRecord) {
    return null;
  }

  @Override
  protected @Nullable Long messagePayloadSize(ProducerRecord<?, ?> producerRecord) {
    return null;
  }

  @Override
  protected @Nullable Long messagePayloadCompressedSize(ProducerRecord<?, ?> producerRecord) {
    return null;
  }

  @Override
  protected @Nullable String messageId(ProducerRecord<?, ?> producerRecord, @Nullable Void unused) {
    return null;
  }
}
