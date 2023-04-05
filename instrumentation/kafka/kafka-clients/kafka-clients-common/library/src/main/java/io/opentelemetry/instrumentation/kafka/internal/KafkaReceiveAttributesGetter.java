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
import org.apache.kafka.common.TopicPartition;

enum KafkaReceiveAttributesGetter implements MessagingAttributesGetter<KafkaReceiveRequest, Void> {
  INSTANCE;

  @Override
  public String getSystem(KafkaReceiveRequest request) {
    return "kafka";
  }

  @Override
  public String getDestinationKind(KafkaReceiveRequest request) {
    return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
  }

  @Override
  @Nullable
  public String getDestination(KafkaReceiveRequest request) {
    Set<String> topics =
        request.getRecords().partitions().stream()
            .map(TopicPartition::topic)
            .collect(Collectors.toSet());
    // only return topic when there's exactly one in the batch
    return topics.size() == 1 ? topics.iterator().next() : null;
  }

  @Override
  public boolean isTemporaryDestination(KafkaReceiveRequest request) {
    return false;
  }

  @Override
  @Nullable
  public String getConversationId(KafkaReceiveRequest request) {
    return null;
  }

  @Override
  @Nullable
  public Long getMessagePayloadSize(KafkaReceiveRequest request) {
    return null;
  }

  @Override
  @Nullable
  public Long getMessagePayloadCompressedSize(KafkaReceiveRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getMessageId(KafkaReceiveRequest request, @Nullable Void unused) {
    return null;
  }

  @Override
  public List<String> getMessageHeader(KafkaReceiveRequest request, String name) {
    return StreamSupport.stream(request.getRecords().spliterator(), false)
        .flatMap(
            consumerRecord ->
                StreamSupport.stream(consumerRecord.headers().headers(name).spliterator(), false))
        .map(header -> new String(header.value(), StandardCharsets.UTF_8))
        .collect(Collectors.toList());
  }
}
