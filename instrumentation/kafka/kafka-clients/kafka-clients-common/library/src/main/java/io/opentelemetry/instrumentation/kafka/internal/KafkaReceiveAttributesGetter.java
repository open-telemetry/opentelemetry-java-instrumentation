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
  public String system(ConsumerRecords<?, ?> consumerRecords) {
    return "kafka";
  }

  @Override
  public String destinationKind(ConsumerRecords<?, ?> consumerRecords) {
    return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
  }

  @Override
  @Nullable
  public String destination(ConsumerRecords<?, ?> consumerRecords) {
    Set<String> topics =
        consumerRecords.partitions().stream()
            .map(TopicPartition::topic)
            .collect(Collectors.toSet());
    // only return topic when there's exactly one in the batch
    return topics.size() == 1 ? topics.iterator().next() : null;
  }

  @Override
  public boolean temporaryDestination(ConsumerRecords<?, ?> consumerRecords) {
    return false;
  }

  @Override
  @Nullable
  public String protocol(ConsumerRecords<?, ?> consumerRecords) {
    return null;
  }

  @Override
  @Nullable
  public String protocolVersion(ConsumerRecords<?, ?> consumerRecords) {
    return null;
  }

  @Override
  @Nullable
  public String url(ConsumerRecords<?, ?> consumerRecords) {
    return null;
  }

  @Override
  @Nullable
  public String conversationId(ConsumerRecords<?, ?> consumerRecords) {
    return null;
  }

  @Override
  @Nullable
  public Long messagePayloadSize(ConsumerRecords<?, ?> consumerRecords) {
    return null;
  }

  @Override
  @Nullable
  public Long messagePayloadCompressedSize(ConsumerRecords<?, ?> consumerRecords) {
    return null;
  }

  @Override
  @Nullable
  public String messageId(ConsumerRecords<?, ?> consumerRecords, @Nullable Void unused) {
    return null;
  }
}
