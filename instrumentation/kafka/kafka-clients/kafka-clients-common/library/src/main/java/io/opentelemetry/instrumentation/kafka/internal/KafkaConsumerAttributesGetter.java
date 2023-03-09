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

enum KafkaConsumerAttributesGetter implements MessagingAttributesGetter<KafkaProcessRequest, Void> {
  INSTANCE;

  @Override
  public String getSystem(KafkaProcessRequest request) {
    return "kafka";
  }

  @Override
  public String getDestinationKind(KafkaProcessRequest request) {
    return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
  }

  @Override
  public String getDestination(KafkaProcessRequest request) {
    return request.getRecord().topic();
  }

  @Override
  public boolean isTemporaryDestination(KafkaProcessRequest request) {
    return false;
  }

  @Override
  @Nullable
  public String getProtocol(KafkaProcessRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getProtocolVersion(KafkaProcessRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getConversationId(KafkaProcessRequest request) {
    return null;
  }

  @Override
  public Long getMessagePayloadSize(KafkaProcessRequest request) {
    return (long) request.getRecord().serializedValueSize();
  }

  @Override
  @Nullable
  public Long getMessagePayloadCompressedSize(KafkaProcessRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getMessageId(KafkaProcessRequest request, @Nullable Void unused) {
    return null;
  }

  @Override
  public List<String> getMessageHeader(KafkaProcessRequest request, String name) {
    return StreamSupport.stream(request.getRecord().headers().headers(name).spliterator(), false)
        .map(header -> new String(header.value(), StandardCharsets.UTF_8))
        .collect(Collectors.toList());
  }
}
