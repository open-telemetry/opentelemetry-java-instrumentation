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

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public enum KafkaConsumerAttributesGetter
    implements MessagingAttributesGetter<KafkaConsumerRequest, Void> {
  INSTANCE;

  @Override
  public String getSystem(KafkaConsumerRequest request) {
    return "kafka";
  }

  @Override
  public String getDestinationKind(KafkaConsumerRequest request) {
    return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
  }

  @Override
  public String getDestination(KafkaConsumerRequest request) {
    return request.getConsumerRecord().topic();
  }

  @Override
  public boolean isTemporaryDestination(KafkaConsumerRequest request) {
    return false;
  }

  @Override
  @Nullable
  public String getProtocol(KafkaConsumerRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getProtocolVersion(KafkaConsumerRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getUrl(KafkaConsumerRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getConversationId(KafkaConsumerRequest request) {
    return null;
  }

  @Override
  public Long getMessagePayloadSize(KafkaConsumerRequest request) {
    return (long) request.getConsumerRecord().serializedValueSize();
  }

  @Override
  @Nullable
  public Long getMessagePayloadCompressedSize(KafkaConsumerRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getMessageId(KafkaConsumerRequest request, @Nullable Void unused) {
    return null;
  }

  @Override
  public List<String> getMessageHeader(KafkaConsumerRequest request, String name) {
    return StreamSupport.stream(
            request.getConsumerRecord().headers().headers(name).spliterator(), false)
        .map(header -> new String(header.value(), StandardCharsets.UTF_8))
        .collect(Collectors.toList());
  }
}
