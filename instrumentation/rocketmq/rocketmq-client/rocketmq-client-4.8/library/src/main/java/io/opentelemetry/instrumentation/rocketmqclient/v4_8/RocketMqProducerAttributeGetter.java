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
  public String getSystem(SendMessageContext sendMessageContext) {
    return "rocketmq";
  }

  @Override
  public String getDestinationKind(SendMessageContext sendMessageContext) {
    return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
  }

  @Nullable
  @Override
  public String getDestination(SendMessageContext sendMessageContext) {
    Message message = sendMessageContext.getMessage();
    return message == null ? null : message.getTopic();
  }

  @Override
  public boolean isTemporaryDestination(SendMessageContext sendMessageContext) {
    return false;
  }

  @Nullable
  @Override
  public String getConversationId(SendMessageContext sendMessageContext) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessagePayloadSize(SendMessageContext sendMessageContext) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessagePayloadCompressedSize(SendMessageContext sendMessageContext) {
    return null;
  }

  @Nullable
  @Override
  public String getMessageId(SendMessageContext request, @Nullable Void unused) {
    SendResult sendResult = request.getSendResult();
    return sendResult == null ? null : sendResult.getMsgId();
  }

  @Override
  public List<String> getMessageHeader(SendMessageContext request, String name) {
    String value = request.getMessage().getProperties().get(name);
    if (value != null) {
      return Collections.singletonList(value);
    }
    return Collections.emptyList();
  }
}
