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

enum KafkaBatchProcessAttributesGetter
    implements MessagingAttributesGetter<ConsumerRecords<?, ?>, Void> {
  INSTANCE;

  @Override
  public String getSystem(ConsumerRecords<?, ?> records) {
    return "kafka";
  }

  @Override
  public String getDestinationKind(ConsumerRecords<?, ?> records) {
    return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
  }

  @Nullable
  @Override
  public String getDestination(ConsumerRecords<?, ?> records) {
    Set<String> topics =
        records.partitions().stream().map(TopicPartition::topic).collect(Collectors.toSet());
    // only return topic when there's exactly one in the batch
    return topics.size() == 1 ? topics.iterator().next() : null;
  }

  @Override
  public boolean isTemporaryDestination(ConsumerRecords<?, ?> records) {
    return false;
  }

  @Nullable
  @Override
  public String getProtocol(ConsumerRecords<?, ?> records) {
    return null;
  }

  @Nullable
  @Override
  public String getProtocolVersion(ConsumerRecords<?, ?> records) {
    return null;
  }

  @Nullable
  @Override
  public String getUrl(ConsumerRecords<?, ?> records) {
    return null;
  }

  @Nullable
  @Override
  public String getConversationId(ConsumerRecords<?, ?> records) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessagePayloadSize(ConsumerRecords<?, ?> records) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessagePayloadCompressedSize(ConsumerRecords<?, ?> records) {
    return null;
  }

  @Nullable
  @Override
  public String getMessageId(ConsumerRecords<?, ?> records, @Nullable Void unused) {
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
