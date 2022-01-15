/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmq;

import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;

class RockerMqProducerAttributeExtractor
    extends MessagingAttributesExtractor<SendMessageContext, Void> {
  @Override
  public MessageOperation operation() {
    return MessageOperation.SEND;
  }

  @Override
  protected String system(SendMessageContext sendMessageContext) {
    return "rocketmq";
  }

  @Override
  protected String destinationKind(SendMessageContext sendMessageContext) {
    return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
  }

  @Nullable
  @Override
  protected String destination(SendMessageContext sendMessageContext) {
    Message message = sendMessageContext.getMessage();
    return message == null ? null : message.getTopic();
  }

  @Override
  protected boolean temporaryDestination(SendMessageContext sendMessageContext) {
    return false;
  }

  @Nullable
  @Override
  protected String protocol(SendMessageContext sendMessageContext) {
    return null;
  }

  @Nullable
  @Override
  protected String protocolVersion(SendMessageContext sendMessageContext) {
    return null;
  }

  @Nullable
  @Override
  protected String url(SendMessageContext sendMessageContext) {
    return null;
  }

  @Nullable
  @Override
  protected String conversationId(SendMessageContext sendMessageContext) {
    return null;
  }

  @Nullable
  @Override
  protected Long messagePayloadSize(SendMessageContext sendMessageContext) {
    return null;
  }

  @Nullable
  @Override
  protected Long messagePayloadCompressedSize(SendMessageContext sendMessageContext) {
    return null;
  }

  @Nullable
  @Override
  protected String messageId(SendMessageContext request, @Nullable Void unused) {
    SendResult sendResult = request.getSendResult();
    return sendResult == null ? null : sendResult.getMsgId();
  }
}
