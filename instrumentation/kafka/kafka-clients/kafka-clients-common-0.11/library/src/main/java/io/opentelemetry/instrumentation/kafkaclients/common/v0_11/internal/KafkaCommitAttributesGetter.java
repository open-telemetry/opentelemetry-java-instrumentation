/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.kafka.common.TopicPartition;

final class KafkaCommitAttributesGetter
    implements MessagingAttributesGetter<KafkaCommitRequest, Void> {

  @Override
  public String getSystem(KafkaCommitRequest request) {
    return "kafka";
  }

  @Nullable
  @Override
  public String getDestination(KafkaCommitRequest request) {
    Map<?, ?> offsets = request.getOffsets();
    if (offsets == null || offsets.isEmpty()) {
      return null;
    }

    String destination = null;
    for (Object key : offsets.keySet()) {
      if (!(key instanceof TopicPartition)) {
        return null;
      }
      String topic = ((TopicPartition) key).topic();
      if (destination == null) {
        destination = topic;
      } else if (!destination.equals(topic)) {
        return null;
      }
    }
    return destination;
  }

  @Nullable
  @Override
  public String getDestinationTemplate(KafkaCommitRequest request) {
    return null;
  }

  @Override
  public boolean isTemporaryDestination(KafkaCommitRequest request) {
    return false;
  }

  @Override
  public boolean isAnonymousDestination(KafkaCommitRequest request) {
    return false;
  }

  @Nullable
  @Override
  public String getConversationId(KafkaCommitRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessageBodySize(KafkaCommitRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessageEnvelopeSize(KafkaCommitRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getMessageId(KafkaCommitRequest request, @Nullable Void unused) {
    return null;
  }

  @Nullable
  @Override
  public String getClientId(KafkaCommitRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Long getBatchMessageCount(KafkaCommitRequest request, @Nullable Void unused) {
    return null;
  }

  @Override
  public List<String> getMessageHeader(KafkaCommitRequest request, String name) {
    return emptyList();
  }
}
