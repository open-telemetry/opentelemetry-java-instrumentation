/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit;

import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import javax.annotation.Nullable;
import org.springframework.amqp.core.Message;

enum SpringRabbitMessageAttributesGetter implements MessagingAttributesGetter<Message, Void> {
  INSTANCE;

  @Override
  public String system(Message message) {
    return "rabbitmq";
  }

  @Override
  public String destinationKind(Message message) {
    return "queue";
  }

  @Override
  @Nullable
  public String destination(Message message) {
    return message.getMessageProperties().getReceivedRoutingKey();
  }

  @Override
  public boolean temporaryDestination(Message message) {
    return false;
  }

  @Override
  @Nullable
  public String protocol(Message message) {
    return null;
  }

  @Override
  @Nullable
  public String protocolVersion(Message message) {
    return null;
  }

  @Override
  @Nullable
  public String url(Message message) {
    return null;
  }

  @Override
  @Nullable
  public String conversationId(Message message) {
    return null;
  }

  @Override
  public Long messagePayloadSize(Message message) {
    return message.getMessageProperties().getContentLength();
  }

  @Override
  @Nullable
  public Long messagePayloadCompressedSize(Message message) {
    return null;
  }

  @Override
  @Nullable
  public String messageId(Message message, @Nullable Void unused) {
    return message.getMessageProperties().getMessageId();
  }
}
