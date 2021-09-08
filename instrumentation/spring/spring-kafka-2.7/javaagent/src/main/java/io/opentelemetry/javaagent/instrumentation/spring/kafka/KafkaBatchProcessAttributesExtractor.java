/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.kafka;

import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class KafkaBatchProcessAttributesExtractor
    extends MessagingAttributesExtractor<ConsumerRecords<?, ?>, Void> {
  @Override
  protected String system(ConsumerRecords<?, ?> records) {
    return "kafka";
  }

  @Override
  protected String destinationKind(ConsumerRecords<?, ?> records) {
    return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
  }

  @Override
  protected @Nullable String destination(ConsumerRecords<?, ?> records) {
    Set<String> topics =
        records.partitions().stream().map(TopicPartition::topic).collect(Collectors.toSet());
    // only return topic when there's exactly one in the batch
    return topics.size() == 1 ? topics.iterator().next() : null;
  }

  @Override
  protected boolean temporaryDestination(ConsumerRecords<?, ?> records) {
    return false;
  }

  @Override
  protected @Nullable String protocol(ConsumerRecords<?, ?> records) {
    return null;
  }

  @Override
  protected @Nullable String protocolVersion(ConsumerRecords<?, ?> records) {
    return null;
  }

  @Override
  protected @Nullable String url(ConsumerRecords<?, ?> records) {
    return null;
  }

  @Override
  protected @Nullable String conversationId(ConsumerRecords<?, ?> records) {
    return null;
  }

  @Override
  protected @Nullable Long messagePayloadSize(ConsumerRecords<?, ?> records) {
    return null;
  }

  @Override
  protected @Nullable Long messagePayloadCompressedSize(ConsumerRecords<?, ?> records) {
    return null;
  }

  @Override
  protected MessageOperation operation(ConsumerRecords<?, ?> records) {
    return MessageOperation.PROCESS;
  }

  @Override
  protected @Nullable String messageId(ConsumerRecords<?, ?> records, @Nullable Void unused) {
    return null;
  }
}
