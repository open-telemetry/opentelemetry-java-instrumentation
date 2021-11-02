/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit;

import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesExtractor;
import javax.annotation.Nullable;
import org.springframework.amqp.core.Message;

final class SpringRabbitMessageAttributesExtractor
    extends MessagingAttributesExtractor<Message, Void> {

  @Override
  public MessageOperation operation() {
    return MessageOperation.PROCESS;
  }

  @Override
  protected String system(Message message) {
    return "rabbitmq";
  }

  @Override
  protected String destinationKind(Message message) {
    return "queue";
  }

  @Override
  @Nullable
  protected String destination(Message message) {
    return message.getMessageProperties().getReceivedRoutingKey();
  }

  @Override
  protected boolean temporaryDestination(Message message) {
    return false;
  }

  @Override
  @Nullable
  protected String protocol(Message message) {
    return null;
  }

  @Override
  @Nullable
  protected String protocolVersion(Message message) {
    return null;
  }

  @Override
  @Nullable
  protected String url(Message message) {
    return null;
  }

  @Override
  @Nullable
  protected String conversationId(Message message) {
    return null;
  }

  @Override
  protected Long messagePayloadSize(Message message) {
    return message.getMessageProperties().getContentLength();
  }

  @Override
  @Nullable
  protected Long messagePayloadCompressedSize(Message message) {
    return null;
  }

  @Override
  @Nullable
  protected String messageId(Message message, @Nullable Void unused) {
    return message.getMessageProperties().getMessageId();
  }
}
