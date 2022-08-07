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
  public String system(ConsumerRecords<?, ?> records) {
    return "kafka";
  }

  @Override
  public String destinationKind(ConsumerRecords<?, ?> records) {
    return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
  }

  @Nullable
  @Override
  public String destination(ConsumerRecords<?, ?> records) {
    Set<String> topics =
        records.partitions().stream().map(TopicPartition::topic).collect(Collectors.toSet());
    // only return topic when there's exactly one in the batch
    return topics.size() == 1 ? topics.iterator().next() : null;
  }

  @Override
  public boolean temporaryDestination(ConsumerRecords<?, ?> records) {
    return false;
  }

  @Nullable
  @Override
  public String protocol(ConsumerRecords<?, ?> records) {
    return null;
  }

  @Nullable
  @Override
  public String protocolVersion(ConsumerRecords<?, ?> records) {
    return null;
  }

  @Nullable
  @Override
  public String url(ConsumerRecords<?, ?> records) {
    return null;
  }

  @Nullable
  @Override
  public String conversationId(ConsumerRecords<?, ?> records) {
    return null;
  }

  @Nullable
  @Override
  public Long messagePayloadSize(ConsumerRecords<?, ?> records) {
    return null;
  }

  @Nullable
  @Override
  public Long messagePayloadCompressedSize(ConsumerRecords<?, ?> records) {
    return null;
  }

  @Nullable
  @Override
  public String messageId(ConsumerRecords<?, ?> records, @Nullable Void unused) {
    return null;
  }

  @Override
  public List<String> header(ConsumerRecords<?, ?> records, String name) {
    return StreamSupport.stream(records.spliterator(), false)
        .flatMap(
            consumerRecord ->
                StreamSupport.stream(consumerRecord.headers().headers(name).spliterator(), false))
        .map(header -> new String(header.value(), StandardCharsets.UTF_8))
        .collect(Collectors.toList());
  }

  @Nullable
  @Override
  public String messagePayload(ConsumerRecords<?, ?> consumerRecords) {
    return null;
  }
}
