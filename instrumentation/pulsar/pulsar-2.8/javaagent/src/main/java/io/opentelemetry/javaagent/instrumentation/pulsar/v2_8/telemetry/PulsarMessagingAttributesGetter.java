/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.apache.pulsar.common.naming.TopicName;

final class PulsarMessagingAttributesGetter
    implements MessagingAttributesGetter<PulsarRequest, Void> {

  @Override
  public String getSystem(PulsarRequest request) {
    return "pulsar";
  }

  @Nullable
  @Override
  public String getDestination(PulsarRequest request) {
    return request.getDestination();
  }

  @Nullable
  @Override
  public String getDestinationTemplate(PulsarRequest request) {
    return null;
  }

  @Override
  public boolean isTemporaryDestination(PulsarRequest request) {
    return false;
  }

  @Override
  public boolean isAnonymousDestination(PulsarRequest request) {
    return false;
  }

  @Nullable
  @Override
  public String getConversationId(PulsarRequest message) {
    return null;
  }

  @Override
  public Long getMessageBodySize(PulsarRequest request) {
    return request.hasMessage() ? (long) request.getMessage().size() : null;
  }

  @Nullable
  @Override
  public Long getMessageEnvelopeSize(PulsarRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getMessageId(PulsarRequest request, @Nullable Void response) {
    return request.hasMessage()
        ? Objects.toString(request.getMessage().getMessageId(), null)
        : null;
  }

  @Nullable
  @Override
  public String getClientId(PulsarRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Long getBatchMessageCount(PulsarRequest request, @Nullable Void unused) {
    return request.hasMessage() ? null : 0L;
  }

  @Nullable
  @Override
  public String getDestinationPartitionId(PulsarRequest request) {
    String destination = request.getDestination();
    if (destination == null) {
      return null;
    }
    int partitionIndex = TopicName.getPartitionIndex(destination);
    if (partitionIndex == -1) {
      return null;
    }
    return String.valueOf(partitionIndex);
  }

  @Nullable
  @Override
  public String getDestinationSubscriptionName(PulsarRequest request) {
    return request.getSubscription();
  }

  @Override
  public List<String> getMessageHeader(PulsarRequest request, String name) {
    if (!request.hasMessage()) {
      return emptyList();
    }
    String value = request.getMessage().getProperty(name);
    return value != null ? singletonList(value) : emptyList();
  }
}
