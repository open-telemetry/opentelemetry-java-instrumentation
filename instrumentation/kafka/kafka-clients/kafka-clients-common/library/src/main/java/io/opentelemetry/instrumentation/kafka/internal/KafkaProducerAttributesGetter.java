/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka.internal;

import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
enum KafkaProducerAttributesGetter
    implements MessagingAttributesGetter<ProducerRecord<?, ?>, RecordMetadata> {
  INSTANCE;

  @Override
  public String getSystem(ProducerRecord<?, ?> producerRecord) {
    return "kafka";
  }

  @Override
  public String getDestinationKind(ProducerRecord<?, ?> producerRecord) {
    return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
  }

  @Override
  public String getDestination(ProducerRecord<?, ?> producerRecord) {
    return producerRecord.topic();
  }

  @Override
  public boolean isTemporaryDestination(ProducerRecord<?, ?> producerRecord) {
    return false;
  }

  @Override
  @Nullable
  public String getProtocol(ProducerRecord<?, ?> producerRecord) {
    return null;
  }

  @Override
  @Nullable
  public String getProtocolVersion(ProducerRecord<?, ?> producerRecord) {
    return null;
  }

  @Override
  @Nullable
  public String getUrl(ProducerRecord<?, ?> producerRecord) {
    return null;
  }

  @Override
  @Nullable
  public String getConversationId(ProducerRecord<?, ?> producerRecord) {
    return null;
  }

  @Override
  @Nullable
  public Long getMessagePayloadSize(ProducerRecord<?, ?> producerRecord) {
    return null;
  }

  @Override
  @Nullable
  public Long getMessagePayloadCompressedSize(ProducerRecord<?, ?> producerRecord) {
    return null;
  }

  @Override
  @Nullable
  public String getMessageId(
      ProducerRecord<?, ?> producerRecord, @Nullable RecordMetadata recordMetadata) {
    return null;
  }

  @Override
  public List<String> getMessageHeader(ProducerRecord<?, ?> producerRecord, String name) {
    return StreamSupport.stream(producerRecord.headers().headers(name).spliterator(), false)
        .map(header -> new String(header.value(), StandardCharsets.UTF_8))
        .collect(Collectors.toList());
  }
}
