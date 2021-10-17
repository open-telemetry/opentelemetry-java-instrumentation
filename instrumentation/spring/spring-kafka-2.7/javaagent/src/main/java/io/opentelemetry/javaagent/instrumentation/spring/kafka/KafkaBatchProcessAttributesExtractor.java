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
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;

public final class KafkaBatchProcessAttributesExtractor
    extends MessagingAttributesExtractor<ConsumerRecords<?, ?>, Void> {

  @Override
  public MessageOperation operation() {
    return MessageOperation.PROCESS;
  }

  @Override
  protected String system(ConsumerRecords<?, ?> records) {
    return "kafka";
  }

  @Override
  protected String destinationKind(ConsumerRecords<?, ?> records) {
    return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
  }

  @Nullable
  @Override
  protected String destination(ConsumerRecords<?, ?> records) {
    Set<String> topics =
        records.partitions().stream().map(TopicPartition::topic).collect(Collectors.toSet());
    // only return topic when there's exactly one in the batch
    return topics.size() == 1 ? topics.iterator().next() : null;
  }

  @Override
  protected boolean temporaryDestination(ConsumerRecords<?, ?> records) {
    return false;
  }

  @Nullable
  @Override
  protected String protocol(ConsumerRecords<?, ?> records) {
    return null;
  }

  @Nullable
  @Override
  protected String protocolVersion(ConsumerRecords<?, ?> records) {
    return null;
  }

  @Nullable
  @Override
  protected String url(ConsumerRecords<?, ?> records) {
    return null;
  }

  @Nullable
  @Override
  protected String conversationId(ConsumerRecords<?, ?> records) {
    return null;
  }

  @Nullable
  @Override
  protected Long messagePayloadSize(ConsumerRecords<?, ?> records) {
    return null;
  }

  @Nullable
  @Override
  protected Long messagePayloadCompressedSize(ConsumerRecords<?, ?> records) {
    return null;
  }

  @Nullable
  @Override
  protected String messageId(ConsumerRecords<?, ?> records, @Nullable Void unused) {
    return null;
  }
}
