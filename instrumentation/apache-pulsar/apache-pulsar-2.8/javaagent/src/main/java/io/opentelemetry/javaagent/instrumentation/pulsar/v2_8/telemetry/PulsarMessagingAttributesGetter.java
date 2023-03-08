/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import org.apache.pulsar.client.api.Message;

enum PulsarMessagingAttributesGetter implements MessagingAttributesGetter<Message<?>, Attributes> {
  INSTANCE;

  @Override
  public String getSystem(Message<?> message) {
    return "pulsar";
  }

  @Override
  public String getDestinationKind(Message<?> message) {
    return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
  }

  @Nullable
  @Override
  public String getDestination(Message<?> message) {
    return null;
  }

  @Override
  public boolean isTemporaryDestination(Message<?> message) {
    return false;
  }

  @Nullable
  @Override
  public String getProtocol(Message<?> message) {
    return null;
  }

  @Nullable
  @Override
  public String getProtocolVersion(Message<?> message) {
    return null;
  }

  @Nullable
  @Override
  public String getConversationId(Message<?> message) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessagePayloadSize(Message<?> message) {
    if (message != null) {
      return (long) message.size();
    }

    return null;
  }

  @Nullable
  @Override
  public Long getMessagePayloadCompressedSize(Message<?> message) {
    return null;
  }

  @Nullable
  @Override
  public String getMessageId(Message<?> message, @Nullable Attributes attributes) {
    if (message != null && message.getMessageId() != null) {
      return message.getMessageId().toString();
    }

    return null;
  }
}
