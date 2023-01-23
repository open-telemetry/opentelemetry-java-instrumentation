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
  public String getSystem(ReceiveMessageRequest request) {
    return "rocketmq";
  }

  @Nullable
  @Override
  public String getDestinationKind(ReceiveMessageRequest request) {
    return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
  }

  @Nullable
  @Override
  public String getDestination(ReceiveMessageRequest request) {
    return request.getMessageQueue().getTopic().getName();
  }

  @Override
  public boolean isTemporaryDestination(ReceiveMessageRequest request) {
    return false;
  }

  @Nullable
  @Override
  public String getProtocol(ReceiveMessageRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getProtocolVersion(ReceiveMessageRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getUrl(ReceiveMessageRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getConversationId(ReceiveMessageRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessagePayloadSize(ReceiveMessageRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessagePayloadCompressedSize(ReceiveMessageRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getMessageId(ReceiveMessageRequest request, @Nullable List<MessageView> unused) {
    return null;
  }
}
