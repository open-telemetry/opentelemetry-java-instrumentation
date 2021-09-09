/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients;

import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.kafka.common.TopicPartition;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class KafkaReceiveAttributesExtractor
    extends MessagingAttributesExtractor<ReceivedRecords, Void> {

  @Override
  protected String system(ReceivedRecords receivedRecords) {
    return "kafka";
  }

  @Override
  protected String destinationKind(ReceivedRecords receivedRecords) {
    return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
  }

  @Override
  protected @Nullable String destination(ReceivedRecords receivedRecords) {
    Set<String> topics =
        receivedRecords.records().partitions().stream()
            .map(TopicPartition::topic)
            .collect(Collectors.toSet());
    // only return topic when there's exactly one in the batch
    return topics.size() == 1 ? topics.iterator().next() : null;
  }

  @Override
  protected boolean temporaryDestination(ReceivedRecords receivedRecords) {
    return false;
  }

  @Override
  protected @Nullable String protocol(ReceivedRecords receivedRecords) {
    return null;
  }

  @Override
  protected @Nullable String protocolVersion(ReceivedRecords receivedRecords) {
    return null;
  }

  @Override
  protected @Nullable String url(ReceivedRecords receivedRecords) {
    return null;
  }

  @Override
  protected @Nullable String conversationId(ReceivedRecords receivedRecords) {
    return null;
  }

  @Override
  protected @Nullable Long messagePayloadSize(ReceivedRecords receivedRecords) {
    return null;
  }

  @Override
  protected @Nullable Long messagePayloadCompressedSize(ReceivedRecords receivedRecords) {
    return null;
  }

  @Override
  protected MessageOperation operation(ReceivedRecords receivedRecords) {
    return MessageOperation.RECEIVE;
  }

  @Override
  protected @Nullable String messageId(ReceivedRecords receivedRecords, @Nullable Void unused) {
    return null;
  }
}
