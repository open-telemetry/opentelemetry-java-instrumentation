/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.common.naming.TopicName;

final class PulsarBatchMessagingAttributesGetter
    implements MessagingAttributesGetter<PulsarBatchRequest, Void> {

  @Override
  public String getSystem(PulsarBatchRequest request) {
    return "pulsar";
  }

  @Nullable
  @Override
  public String getDestination(PulsarBatchRequest request) {
    return request.getDestination();
  }

  @Nullable
  @Override
  public String getDestinationTemplate(PulsarBatchRequest request) {
    return null;
  }

  @Override
  public boolean isTemporaryDestination(PulsarBatchRequest request) {
    return false;
  }

  @Override
  public boolean isAnonymousDestination(PulsarBatchRequest request) {
    return false;
  }

  @Nullable
  @Override
  public String getConversationId(PulsarBatchRequest message) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessageBodySize(PulsarBatchRequest request) {
    long size = 0;
    boolean hasMessages = false;
    for (Message<?> message : request.getMessages()) {
      hasMessages = true;
      size += message.size();
    }
    return hasMessages ? size : null;
  }

  @Nullable
  @Override
  public Long getMessageEnvelopeSize(PulsarBatchRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getMessageId(PulsarBatchRequest request, @Nullable Void response) {
    return null;
  }

  @Nullable
  @Override
  public String getClientId(PulsarBatchRequest request) {
    return null;
  }

  @Override
  public Long getBatchMessageCount(PulsarBatchRequest request, @Nullable Void unused) {
    return (long) request.getMessages().size();
  }

  @Nullable
  @Override
  public String getDestinationPartitionId(PulsarBatchRequest request) {
    int partitionIndex = TopicName.getPartitionIndex(request.getDestination());
    if (partitionIndex == -1) {
      return null;
    }
    return String.valueOf(partitionIndex);
  }

  @Override
  public List<String> getMessageHeader(PulsarBatchRequest request, String name) {
    List<String> values = null;
    for (Message<?> message : request.getMessages()) {
      String value = message.getProperty(name);
      if (value != null) {
        if (values == null) {
          values = new ArrayList<>();
        }
        values.add(value);
      }
    }
    return values == null ? emptyList() : values;
  }
}
