/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v28.telemetry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import org.apache.pulsar.client.api.Message;

enum PulsarMessagingAttributesGetter implements MessagingAttributesGetter<Message<?>, Attributes> {
  INSTANCE;

  @Nullable
  @Override
  public String system(Message<?> message) {
    return "pulsar";
  }

  @Nullable
  @Override
  public String destinationKind(Message<?> message) {
    return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
  }

  @Nullable
  @Override
  public String destination(Message<?> message) {
    return null;
  }

  @Override
  public boolean temporaryDestination(Message<?> message) {
    return false;
  }

  @Nullable
  @Override
  public String protocol(Message<?> message) {
    return null;
  }

  @Nullable
  @Override
  public String protocolVersion(Message<?> message) {
    return null;
  }

  @Nullable
  @Override
  public String url(Message<?> message) {
    return null;
  }

  @Nullable
  @Override
  public String conversationId(Message<?> message) {
    return null;
  }

  @Nullable
  @Override
  public Long messagePayloadSize(Message<?> message) {
    if (null != message) {
      return (long) message.size();
    }

    return null;
  }

  @Nullable
  @Override
  public Long messagePayloadCompressedSize(Message<?> message) {
    return null;
  }

  @Nullable
  @Override
  public String messageId(Message<?> message, @Nullable Attributes attributes) {
    String messageId0 = null;
    if (null != message && null != message.getMessageId()) {
      messageId0 = message.getMessageId().toString();
    }

    return messageId0;
  }
}
