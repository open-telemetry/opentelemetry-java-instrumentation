/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka.internal;

import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.kafka.common.TopicPartition;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class KafkaReceiveAttributesExtractor
    extends MessagingAttributesExtractor<ReceivedRecords, Void> {

  @Override
  public MessageOperation operation() {
    return MessageOperation.RECEIVE;
  }

  @Override
  protected String system(ReceivedRecords receivedRecords) {
    return "kafka";
  }

  @Override
  protected String destinationKind(ReceivedRecords receivedRecords) {
    return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
  }

  @Override
  @Nullable
  protected String destination(ReceivedRecords receivedRecords) {
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
  @Nullable
  protected String protocol(ReceivedRecords receivedRecords) {
    return null;
  }

  @Override
  @Nullable
  protected String protocolVersion(ReceivedRecords receivedRecords) {
    return null;
  }

  @Override
  @Nullable
  protected String url(ReceivedRecords receivedRecords) {
    return null;
  }

  @Override
  @Nullable
  protected String conversationId(ReceivedRecords receivedRecords) {
    return null;
  }

  @Override
  @Nullable
  protected Long messagePayloadSize(ReceivedRecords receivedRecords) {
    return null;
  }

  @Override
  @Nullable
  protected Long messagePayloadCompressedSize(ReceivedRecords receivedRecords) {
    return null;
  }

  @Override
  @Nullable
  protected String messageId(ReceivedRecords receivedRecords, @Nullable Void unused) {
    return null;
  }
}
