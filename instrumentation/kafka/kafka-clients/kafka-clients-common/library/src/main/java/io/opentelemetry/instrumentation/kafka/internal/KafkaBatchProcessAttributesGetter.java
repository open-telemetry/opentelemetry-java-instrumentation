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

enum KafkaBatchProcessAttributesGetter
    implements MessagingAttributesGetter<KafkaBatchRequest, Void> {
  INSTANCE;

  @Override
  public String getSystem(KafkaBatchRequest request) {
    return "kafka";
  }

  @Override
  public String getDestinationKind(KafkaBatchRequest request) {
    return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
  }

  @Nullable
  @Override
  public String getDestination(KafkaBatchRequest request) {
    Set<String> topics =
        request.getConsumerRecords().partitions().stream()
            .map(TopicPartition::topic)
            .collect(Collectors.toSet());
    // only return topic when there's exactly one in the batch
    return topics.size() == 1 ? topics.iterator().next() : null;
  }

  @Override
  public boolean isTemporaryDestination(KafkaBatchRequest request) {
    return false;
  }

  @Nullable
  @Override
  public String getProtocol(KafkaBatchRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getProtocolVersion(KafkaBatchRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getUrl(KafkaBatchRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getConversationId(KafkaBatchRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessagePayloadSize(KafkaBatchRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessagePayloadCompressedSize(KafkaBatchRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getMessageId(KafkaBatchRequest request, @Nullable Void unused) {
    return null;
  }

  @Override
  public List<String> getMessageHeader(KafkaBatchRequest request, String name) {
    return StreamSupport.stream(request.getConsumerRecords().spliterator(), false)
        .flatMap(
            consumerRecord ->
                StreamSupport.stream(consumerRecord.headers().headers(name).spliterator(), false))
        .map(header -> new String(header.value(), StandardCharsets.UTF_8))
        .collect(Collectors.toList());
  }
}
