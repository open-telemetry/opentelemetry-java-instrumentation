/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import apache.rocketmq.v2.ReceiveMessageRequest;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.apis.message.MessageView;

enum RocketMqConsumerReceiveAttributeGetter
    implements MessagingAttributesGetter<ReceiveMessageRequest, List<MessageView>> {
  INSTANCE;

  @Nullable
  @Override
  public String system(ReceiveMessageRequest request) {
    return "rocketmq";
  }

  @Nullable
  @Override
  public String destinationKind(ReceiveMessageRequest request) {
    return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
  }

  @Nullable
  @Override
  public String destination(ReceiveMessageRequest request) {
    return request.getMessageQueue().getTopic().getName();
  }

  @Override
  public boolean temporaryDestination(ReceiveMessageRequest request) {
    return false;
  }

  @Nullable
  @Override
  public String protocol(ReceiveMessageRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String protocolVersion(ReceiveMessageRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String url(ReceiveMessageRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String conversationId(ReceiveMessageRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Long messagePayloadSize(ReceiveMessageRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Long messagePayloadCompressedSize(ReceiveMessageRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String messageId(ReceiveMessageRequest request, @Nullable List<MessageView> unused) {
    return null;
  }
}
