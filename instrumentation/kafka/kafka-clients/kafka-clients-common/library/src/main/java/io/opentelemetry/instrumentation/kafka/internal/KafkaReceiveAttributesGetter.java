/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka.internal;

import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;

enum KafkaReceiveAttributesGetter
    implements MessagingAttributesGetter<ConsumerAndRecord<ConsumerRecords<?, ?>>, Void> {
  INSTANCE;

  @Override
  public String getSystem(ConsumerAndRecord<ConsumerRecords<?, ?>> consumerAndRecords) {
    return "kafka";
  }

  @Override
  public String getDestinationKind(ConsumerAndRecord<ConsumerRecords<?, ?>> consumerAndRecords) {
    return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
  }

  @Override
  @Nullable
  public String getDestination(ConsumerAndRecord<ConsumerRecords<?, ?>> consumerAndRecords) {
    Set<String> topics =
        consumerAndRecords.record().partitions().stream()
            .map(TopicPartition::topic)
            .collect(Collectors.toSet());
    // only return topic when there's exactly one in the batch
    return topics.size() == 1 ? topics.iterator().next() : null;
  }

  @Override
  public boolean isTemporaryDestination(
      ConsumerAndRecord<ConsumerRecords<?, ?>> consumerAndRecords) {
    return false;
  }

  @Override
  @Nullable
  public String getProtocol(ConsumerAndRecord<ConsumerRecords<?, ?>> consumerAndRecords) {
    return null;
  }

  @Override
  @Nullable
  public String getProtocolVersion(ConsumerAndRecord<ConsumerRecords<?, ?>> consumerAndRecords) {
    return null;
  }

  @Override
  @Nullable
  public String getUrl(ConsumerAndRecord<ConsumerRecords<?, ?>> consumerAndRecords) {
    return null;
  }

  @Override
  @Nullable
  public String getConversationId(ConsumerAndRecord<ConsumerRecords<?, ?>> consumerAndRecords) {
    return null;
  }

  @Override
  @Nullable
  public Long getMessagePayloadSize(ConsumerAndRecord<ConsumerRecords<?, ?>> consumerAndRecords) {
    return null;
  }

  @Override
  @Nullable
  public Long getMessagePayloadCompressedSize(
      ConsumerAndRecord<ConsumerRecords<?, ?>> consumerAndRecords) {
    return null;
  }

  @Override
  @Nullable
  public String getMessageId(
      ConsumerAndRecord<ConsumerRecords<?, ?>> consumerAndRecords, @Nullable Void unused) {
    return null;
  }

  @Override
  public List<String> getMessageHeader(
      ConsumerAndRecord<ConsumerRecords<?, ?>> consumerAndRecords, String name) {
    return StreamSupport.stream(consumerAndRecords.record().spliterator(), false)
        .flatMap(
            consumerRecord ->
                StreamSupport.stream(consumerRecord.headers().headers(name).spliterator(), false))
        .map(header -> new String(header.value(), StandardCharsets.UTF_8))
        .collect(Collectors.toList());
  }
}
