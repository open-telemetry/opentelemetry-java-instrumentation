/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;

enum RocketMqConsumerProcessAttributeGetter
    implements MessagingAttributesGetter<MessageView, ConsumeResult> {
  INSTANCE;

  @Nullable
  @Override
  public String getSystem(MessageView messageView) {
    return "rocketmq";
  }

  @Nullable
  @Override
  public String getDestinationKind(MessageView messageView) {
    return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
  }

  @Nullable
  @Override
  public String getDestination(MessageView messageView) {
    return messageView.getTopic();
  }

  @Override
  public boolean isTemporaryDestination(MessageView messageView) {
    return false;
  }

  @Nullable
  @Override
  public String getProtocol(MessageView messageView) {
    return null;
  }

  @Nullable
  @Override
  public String getProtocolVersion(MessageView messageView) {
    return null;
  }

  @Nullable
  @Override
  public String getConversationId(MessageView messageView) {
    return null;
  }

  @Override
  public Long getMessagePayloadSize(MessageView messageView) {
    return (long) messageView.getBody().remaining();
  }

  @Nullable
  @Override
  public Long getMessagePayloadCompressedSize(MessageView messageView) {
    return null;
  }

  @Nullable
  @Override
  public String getMessageId(MessageView messageView, @Nullable ConsumeResult unused) {
    return messageView.getMessageId().toString();
  }

  @Override
  public List<String> getMessageHeader(MessageView messageView, String name) {
    String value = messageView.getProperties().get(name);
    if (value != null) {
      return Collections.singletonList(value);
    }
    return Collections.emptyList();
  }
}
