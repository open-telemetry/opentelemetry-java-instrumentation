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
import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public enum KafkaConsumerAttributesGetter
    implements MessagingAttributesGetter<ConsumerRecord<?, ?>, Void> {
  INSTANCE;

  @Override
  public String getSystem(ConsumerRecord<?, ?> consumerRecord) {
    return "kafka";
  }

  @Override
  public String getDestinationKind(ConsumerRecord<?, ?> consumerRecord) {
    return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
  }

  @Override
  public String getDestination(ConsumerRecord<?, ?> consumerRecord) {
    return consumerRecord.topic();
  }

  @Override
  public boolean isTemporaryDestination(ConsumerRecord<?, ?> consumerRecord) {
    return false;
  }

  @Override
  @Nullable
  public String getProtocol(ConsumerRecord<?, ?> consumerRecord) {
    return null;
  }

  @Override
  @Nullable
  public String getProtocolVersion(ConsumerRecord<?, ?> consumerRecord) {
    return null;
  }

  @Override
  @Nullable
  public String getUrl(ConsumerRecord<?, ?> consumerRecord) {
    return null;
  }

  @Override
  @Nullable
  public String getConversationId(ConsumerRecord<?, ?> consumerRecord) {
    return null;
  }

  @Override
  public Long getMessagePayloadSize(ConsumerRecord<?, ?> consumerRecord) {
    return (long) consumerRecord.serializedValueSize();
  }

  @Override
  @Nullable
  public Long getMessagePayloadCompressedSize(ConsumerRecord<?, ?> consumerRecord) {
    return null;
  }

  @Override
  @Nullable
  public String getMessageId(ConsumerRecord<?, ?> consumerRecord, @Nullable Void unused) {
    return null;
  }

  @Override
  public List<String> getMessageHeader(ConsumerRecord<?, ?> consumerRecord, String name) {
    return StreamSupport.stream(consumerRecord.headers().headers(name).spliterator(), false)
        .map(header -> new String(header.value(), StandardCharsets.UTF_8))
        .collect(Collectors.toList());
  }
}
