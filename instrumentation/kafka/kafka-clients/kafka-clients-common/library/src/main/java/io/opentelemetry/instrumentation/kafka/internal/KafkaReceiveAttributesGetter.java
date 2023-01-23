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

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public enum KafkaReceiveAttributesGetter
    implements MessagingAttributesGetter<ConsumerRecords<?, ?>, Void> {
  INSTANCE;

  @Override
  public String getSystem(ConsumerRecords<?, ?> consumerRecords) {
    return "kafka";
  }

  @Override
  public String getDestinationKind(ConsumerRecords<?, ?> consumerRecords) {
    return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
  }

  @Override
  @Nullable
  public String getDestination(ConsumerRecords<?, ?> consumerRecords) {
    Set<String> topics =
        consumerRecords.partitions().stream()
            .map(TopicPartition::topic)
            .collect(Collectors.toSet());
    // only return topic when there's exactly one in the batch
    return topics.size() == 1 ? topics.iterator().next() : null;
  }

  @Override
  public boolean isTemporaryDestination(ConsumerRecords<?, ?> consumerRecords) {
    return false;
  }

  @Override
  @Nullable
  public String getProtocol(ConsumerRecords<?, ?> consumerRecords) {
    return null;
  }

  @Override
  @Nullable
  public String getProtocolVersion(ConsumerRecords<?, ?> consumerRecords) {
    return null;
  }

  @Override
  @Nullable
  public String getUrl(ConsumerRecords<?, ?> consumerRecords) {
    return null;
  }

  @Override
  @Nullable
  public String getConversationId(ConsumerRecords<?, ?> consumerRecords) {
    return null;
  }

  @Override
  @Nullable
  public Long getMessagePayloadSize(ConsumerRecords<?, ?> consumerRecords) {
    return null;
  }

  @Override
  @Nullable
  public Long getMessagePayloadCompressedSize(ConsumerRecords<?, ?> consumerRecords) {
    return null;
  }

  @Override
  @Nullable
  public String getMessageId(ConsumerRecords<?, ?> consumerRecords, @Nullable Void unused) {
    return null;
  }

  @Override
  public List<String> getMessageHeader(ConsumerRecords<?, ?> records, String name) {
    return StreamSupport.stream(records.spliterator(), false)
        .flatMap(
            consumerRecord ->
                StreamSupport.stream(consumerRecord.headers().headers(name).spliterator(), false))
        .map(header -> new String(header.value(), StandardCharsets.UTF_8))
        .collect(Collectors.toList());
  }
}
