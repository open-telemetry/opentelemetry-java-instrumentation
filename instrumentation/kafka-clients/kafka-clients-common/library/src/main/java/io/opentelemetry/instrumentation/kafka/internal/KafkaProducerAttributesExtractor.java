/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka.internal;

import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import org.apache.kafka.clients.producer.ProducerRecord;

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
  @Nullable
  protected String protocol(ProducerRecord<?, ?> producerRecord) {
    return null;
  }

  @Override
  @Nullable
  protected String protocolVersion(ProducerRecord<?, ?> producerRecord) {
    return null;
  }

  @Override
  @Nullable
  protected String url(ProducerRecord<?, ?> producerRecord) {
    return null;
  }

  @Override
  @Nullable
  protected String conversationId(ProducerRecord<?, ?> producerRecord) {
    return null;
  }

  @Override
  @Nullable
  protected Long messagePayloadSize(ProducerRecord<?, ?> producerRecord) {
    return null;
  }

  @Override
  @Nullable
  protected Long messagePayloadCompressedSize(ProducerRecord<?, ?> producerRecord) {
    return null;
  }

  @Override
  @Nullable
  protected String messageId(ProducerRecord<?, ?> producerRecord, @Nullable Void unused) {
    return null;
  }
}
