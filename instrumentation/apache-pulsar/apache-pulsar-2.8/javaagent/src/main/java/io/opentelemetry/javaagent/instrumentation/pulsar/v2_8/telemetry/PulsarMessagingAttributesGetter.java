/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.pulsar.client.api.Message;

enum PulsarMessagingAttributesGetter implements MessagingAttributesGetter<PulsarRequest, Void> {
  INSTANCE;

  @Override
  public String getSystem(PulsarRequest request) {
    return "pulsar";
  }

  @Override
  public String getDestinationKind(PulsarRequest request) {
    return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
  }

  @Nullable
  @Override
  public String getDestination(PulsarRequest request) {
    return request.getDestination();
  }

  @Override
  public boolean isTemporaryDestination(PulsarRequest request) {
    return false;
  }

  @Nullable
  @Override
  public String getConversationId(PulsarRequest message) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessagePayloadSize(PulsarRequest request) {
    return (long) request.getMessage().size();
  }

  @Nullable
  @Override
  public Long getMessagePayloadCompressedSize(PulsarRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getMessageId(PulsarRequest request, @Nullable Void response) {
    Message<?> message = request.getMessage();
    if (message.getMessageId() != null) {
      return message.getMessageId().toString();
    }

    return null;
  }

  @Override
  public List<String> getMessageHeader(PulsarRequest request, String name) {
    String value = request.getMessage().getProperty(name);
    return value != null ? singletonList(value) : emptyList();
  }
}
