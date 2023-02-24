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

enum KafkaConsumerAttributesGetter
    implements MessagingAttributesGetter<ConsumerAndRecord<ConsumerRecord<?, ?>>, Void> {
  INSTANCE;

  @Override
  public String getSystem(ConsumerAndRecord<ConsumerRecord<?, ?>> consumerAndRecord) {
    return "kafka";
  }

  @Override
  public String getDestinationKind(ConsumerAndRecord<ConsumerRecord<?, ?>> consumerAndRecord) {
    return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
  }

  @Override
  public String getDestination(ConsumerAndRecord<ConsumerRecord<?, ?>> consumerAndRecord) {
    return consumerAndRecord.record().topic();
  }

  @Override
  public boolean isTemporaryDestination(ConsumerAndRecord<ConsumerRecord<?, ?>> consumerAndRecord) {
    return false;
  }

  @Override
  @Nullable
  public String getProtocol(ConsumerAndRecord<ConsumerRecord<?, ?>> consumerAndRecord) {
    return null;
  }

  @Override
  @Nullable
  public String getProtocolVersion(ConsumerAndRecord<ConsumerRecord<?, ?>> consumerAndRecord) {
    return null;
  }

  @Override
  @Nullable
  public String getUrl(ConsumerAndRecord<ConsumerRecord<?, ?>> consumerAndRecord) {
    return null;
  }

  @Override
  @Nullable
  public String getConversationId(ConsumerAndRecord<ConsumerRecord<?, ?>> consumerAndRecord) {
    return null;
  }

  @Override
  public Long getMessagePayloadSize(ConsumerAndRecord<ConsumerRecord<?, ?>> consumerAndRecord) {
    return (long) consumerAndRecord.record().serializedValueSize();
  }

  @Override
  @Nullable
  public Long getMessagePayloadCompressedSize(
      ConsumerAndRecord<ConsumerRecord<?, ?>> consumerAndRecord) {
    return null;
  }

  @Override
  @Nullable
  public String getMessageId(
      ConsumerAndRecord<ConsumerRecord<?, ?>> consumerAndRecord, @Nullable Void unused) {
    return null;
  }

  @Override
  public List<String> getMessageHeader(
      ConsumerAndRecord<ConsumerRecord<?, ?>> consumerAndRecord, String name) {
    return StreamSupport.stream(
            consumerAndRecord.record().headers().headers(name).spliterator(), false)
        .map(header -> new String(header.value(), StandardCharsets.UTF_8))
        .collect(Collectors.toList());
  }
}
