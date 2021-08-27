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
import org.apache.kafka.common.TopicPartition;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class BatchConsumerAttributesExtractor
    extends MessagingAttributesExtractor<BatchRecords<?, ?>, Void> {
  @Override
  protected String system(BatchRecords<?, ?> batchRecords) {
    return "kafka";
  }

  @Override
  protected String destinationKind(BatchRecords<?, ?> batchRecords) {
    return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
  }

  @Override
  protected @Nullable String destination(BatchRecords<?, ?> batchRecords) {
    Set<String> topics =
        batchRecords.records().partitions().stream()
            .map(TopicPartition::topic)
            .collect(Collectors.toSet());
    // only return topic when there's exactly one in the batch
    return topics.size() == 1 ? topics.iterator().next() : null;
  }

  @Override
  protected boolean temporaryDestination(BatchRecords<?, ?> batchRecords) {
    return false;
  }

  @Override
  protected @Nullable String protocol(BatchRecords<?, ?> batchRecords) {
    return null;
  }

  @Override
  protected @Nullable String protocolVersion(BatchRecords<?, ?> batchRecords) {
    return null;
  }

  @Override
  protected @Nullable String url(BatchRecords<?, ?> batchRecords) {
    return null;
  }

  @Override
  protected @Nullable String conversationId(BatchRecords<?, ?> batchRecords) {
    return null;
  }

  @Override
  protected @Nullable Long messagePayloadSize(BatchRecords<?, ?> batchRecords) {
    return null;
  }

  @Override
  protected @Nullable Long messagePayloadCompressedSize(BatchRecords<?, ?> batchRecords) {
    return null;
  }

  @Override
  protected MessageOperation operation(BatchRecords<?, ?> batchRecords) {
    return MessageOperation.PROCESS;
  }

  @Override
  protected @Nullable String messageId(BatchRecords<?, ?> batchRecords, @Nullable Void unused) {
    return null;
  }
}
