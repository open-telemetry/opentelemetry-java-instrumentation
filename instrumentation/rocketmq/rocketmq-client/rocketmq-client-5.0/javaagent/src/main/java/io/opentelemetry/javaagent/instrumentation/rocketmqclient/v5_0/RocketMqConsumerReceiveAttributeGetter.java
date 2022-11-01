/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.java.route.MessageQueueImpl;

enum RocketMqConsumerReceiveAttributeGetter
    implements MessagingAttributesGetter<MessageQueueImpl, List<MessageView>> {
  INSTANCE;

  @Nullable
  @Override
  public String system(MessageQueueImpl messageQueue) {
    return "rocketmq";
  }

  @Nullable
  @Override
  public String destinationKind(MessageQueueImpl messageQueue) {
    return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
  }

  @Nullable
  @Override
  public String destination(MessageQueueImpl messageQueue) {
    return messageQueue.getTopic();
  }

  @Override
  public boolean temporaryDestination(MessageQueueImpl messageQueue) {
    return false;
  }

  @Nullable
  @Override
  public String protocol(MessageQueueImpl messageQueue) {
    return null;
  }

  @Nullable
  @Override
  public String protocolVersion(MessageQueueImpl messageQueue) {
    return null;
  }

  @Nullable
  @Override
  public String url(MessageQueueImpl messageQueue) {
    return null;
  }

  @Nullable
  @Override
  public String conversationId(MessageQueueImpl messageQueue) {
    return null;
  }

  @Nullable
  @Override
  public Long messagePayloadSize(MessageQueueImpl messageQueue) {
    return null;
  }

  @Nullable
  @Override
  public Long messagePayloadCompressedSize(MessageQueueImpl messageQueue) {
    return null;
  }

  @Nullable
  @Override
  public String messageId(MessageQueueImpl messageQueue, @Nullable List<MessageView> unused) {
    return null;
  }
}
