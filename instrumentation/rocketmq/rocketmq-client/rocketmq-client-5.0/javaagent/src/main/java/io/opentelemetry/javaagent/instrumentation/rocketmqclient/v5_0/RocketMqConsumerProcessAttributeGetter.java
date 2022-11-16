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
  public String system(MessageView messageView) {
    return "rocketmq";
  }

  @Nullable
  @Override
  public String destinationKind(MessageView messageView) {
    return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
  }

  @Nullable
  @Override
  public String destination(MessageView messageView) {
    return messageView.getTopic();
  }

  @Override
  public boolean temporaryDestination(MessageView messageView) {
    return false;
  }

  @Nullable
  @Override
  public String protocol(MessageView messageView) {
    return null;
  }

  @Nullable
  @Override
  public String protocolVersion(MessageView messageView) {
    return null;
  }

  @Nullable
  @Override
  public String url(MessageView messageView) {
    return null;
  }

  @Nullable
  @Override
  public String conversationId(MessageView messageView) {
    return null;
  }

  @Nullable
  @Override
  public Long messagePayloadSize(MessageView messageView) {
    return (long) messageView.getBody().remaining();
  }

  @Nullable
  @Override
  public Long messagePayloadCompressedSize(MessageView messageView) {
    return null;
  }

  @Nullable
  @Override
  public String messageId(MessageView messageView, @Nullable ConsumeResult unused) {
    return messageView.getMessageId().toString();
  }

  @Override
  public List<String> header(MessageView messageView, String name) {
    String value = messageView.getProperties().get(name);
    if (value != null) {
      return Collections.singletonList(value);
    }
    return Collections.emptyList();
  }
}
