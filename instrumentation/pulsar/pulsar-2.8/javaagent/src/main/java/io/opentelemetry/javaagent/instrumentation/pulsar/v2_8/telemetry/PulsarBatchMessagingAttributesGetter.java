/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;

enum PulsarBatchMessagingAttributesGetter
    implements MessagingAttributesGetter<PulsarBatchRequest, Void> {
  INSTANCE;

  @Override
  public String getSystem(PulsarBatchRequest request) {
    return "pulsar";
  }

  @Override
  public String getDestinationKind(PulsarBatchRequest request) {
    return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
  }

  @Nullable
  @Override
  public String getDestination(PulsarBatchRequest request) {
    return request.getDestination();
  }

  @Override
  public boolean isTemporaryDestination(PulsarBatchRequest request) {
    return false;
  }

  @Nullable
  @Override
  public String getConversationId(PulsarBatchRequest message) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessagePayloadSize(PulsarBatchRequest request) {
    return StreamSupport.stream(request.getMessages().spliterator(), false)
        .map(message -> (long) message.size())
        .reduce(Long::sum)
        .orElse(null);
  }

  @Nullable
  @Override
  public Long getMessagePayloadCompressedSize(PulsarBatchRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getMessageId(PulsarBatchRequest request, @Nullable Void response) {
    return null;
  }

  @Override
  public List<String> getMessageHeader(PulsarBatchRequest request, String name) {
    return StreamSupport.stream(request.getMessages().spliterator(), false)
        .map(message -> message.getProperty(name))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }
}
