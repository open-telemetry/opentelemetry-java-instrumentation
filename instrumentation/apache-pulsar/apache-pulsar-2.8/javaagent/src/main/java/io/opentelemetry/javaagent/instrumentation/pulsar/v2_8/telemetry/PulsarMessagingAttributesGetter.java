/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
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
  public String getConversationId(Message<?> message) {
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
}
