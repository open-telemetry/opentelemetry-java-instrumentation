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
import org.apache.kafka.clients.producer.RecordMetadata;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
enum KafkaProducerAttributesGetter
    implements MessagingAttributesGetter<KafkaProducerRequest, RecordMetadata> {
  INSTANCE;

  @Override
  public String getSystem(KafkaProducerRequest request) {
    return "kafka";
  }

  @Override
  public String getDestinationKind(KafkaProducerRequest request) {
    return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
  }

  @Override
  public String getDestination(KafkaProducerRequest request) {
    return request.getRecord().topic();
  }

  @Override
  public boolean isTemporaryDestination(KafkaProducerRequest request) {
    return false;
  }

  @Override
  @Nullable
  public String getProtocol(KafkaProducerRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getProtocolVersion(KafkaProducerRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getUrl(KafkaProducerRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getConversationId(KafkaProducerRequest request) {
    return null;
  }

  @Override
  @Nullable
  public Long getMessagePayloadSize(KafkaProducerRequest request) {
    return null;
  }

  @Override
  @Nullable
  public Long getMessagePayloadCompressedSize(KafkaProducerRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getMessageId(
      KafkaProducerRequest request, @Nullable RecordMetadata recordMetadata) {
    return null;
  }

  @Override
  public List<String> getMessageHeader(KafkaProducerRequest request, String name) {
    return StreamSupport.stream(request.getRecord().headers().headers(name).spliterator(), false)
        .map(header -> new String(header.value(), StandardCharsets.UTF_8))
        .collect(Collectors.toList());
  }
}
