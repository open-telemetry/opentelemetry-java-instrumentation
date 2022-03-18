/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka.internal;

import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.kafka.common.TopicPartition;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public enum KafkaReceiveAttributesGetter
    implements MessagingAttributesGetter<ReceivedRecords, Void> {
  INSTANCE;

  @Override
  public String system(ReceivedRecords receivedRecords) {
    return "kafka";
  }

  @Override
  public String destinationKind(ReceivedRecords receivedRecords) {
    return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
  }

  @Override
  @Nullable
  public String destination(ReceivedRecords receivedRecords) {
    Set<String> topics =
        receivedRecords.records().partitions().stream()
            .map(TopicPartition::topic)
            .collect(Collectors.toSet());
    // only return topic when there's exactly one in the batch
    return topics.size() == 1 ? topics.iterator().next() : null;
  }

  @Override
  public boolean temporaryDestination(ReceivedRecords receivedRecords) {
    return false;
  }

  @Override
  @Nullable
  public String protocol(ReceivedRecords receivedRecords) {
    return null;
  }

  @Override
  @Nullable
  public String protocolVersion(ReceivedRecords receivedRecords) {
    return null;
  }

  @Override
  @Nullable
  public String url(ReceivedRecords receivedRecords) {
    return null;
  }

  @Override
  @Nullable
  public String conversationId(ReceivedRecords receivedRecords) {
    return null;
  }

  @Override
  @Nullable
  public Long messagePayloadSize(ReceivedRecords receivedRecords) {
    return null;
  }

  @Override
  @Nullable
  public Long messagePayloadCompressedSize(ReceivedRecords receivedRecords) {
    return null;
  }

  @Override
  @Nullable
  public String messageId(ReceivedRecords receivedRecords, @Nullable Void unused) {
    return null;
  }
}
