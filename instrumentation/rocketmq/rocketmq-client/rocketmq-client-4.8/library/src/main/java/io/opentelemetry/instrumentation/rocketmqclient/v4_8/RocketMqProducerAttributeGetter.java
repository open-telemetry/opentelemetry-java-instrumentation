/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;

enum RocketMqProducerAttributeGetter
    implements MessagingAttributesGetter<SendMessageContext, Void> {
  INSTANCE;

  @Override
  public String system(SendMessageContext sendMessageContext) {
    return "rocketmq";
  }

  @Override
  public String destinationKind(SendMessageContext sendMessageContext) {
    return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
  }

  @Nullable
  @Override
  public String destination(SendMessageContext sendMessageContext) {
    Message message = sendMessageContext.getMessage();
    return message == null ? null : message.getTopic();
  }

  @Override
  public boolean temporaryDestination(SendMessageContext sendMessageContext) {
    return false;
  }

  @Nullable
  @Override
  public String protocol(SendMessageContext sendMessageContext) {
    return null;
  }

  @Nullable
  @Override
  public String protocolVersion(SendMessageContext sendMessageContext) {
    return null;
  }

  @Nullable
  @Override
  public String url(SendMessageContext sendMessageContext) {
    return null;
  }

  @Nullable
  @Override
  public String conversationId(SendMessageContext sendMessageContext) {
    return null;
  }

  @Nullable
  @Override
  public Long messagePayloadSize(SendMessageContext sendMessageContext) {
    return null;
  }

  @Nullable
  @Override
  public Long messagePayloadCompressedSize(SendMessageContext sendMessageContext) {
    return null;
  }

  @Nullable
  @Override
  public String messageId(SendMessageContext request, @Nullable Void unused) {
    SendResult sendResult = request.getSendResult();
    return sendResult == null ? null : sendResult.getMsgId();
  }

  @Override
  public List<String> header(SendMessageContext request, String name) {
    String value = request.getMessage().getProperties().get(name);
    if (value != null) {
      return Collections.singletonList(value);
    }
    return Collections.emptyList();
  }
}
