/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmq;

import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import org.apache.rocketmq.common.message.MessageExt;

enum RocketMqConsumerAttributeGetter implements MessagingAttributesGetter<MessageExt, Void> {
  INSTANCE;

  @Override
  public String system(MessageExt request) {
    return "rocketmq";
  }

  @Override
  public String destinationKind(MessageExt request) {
    return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
  }

  @Override
  public String destination(MessageExt request) {
    return request.getTopic();
  }

  @Override
  public boolean temporaryDestination(MessageExt request) {
    return false;
  }

  @Nullable
  @Override
  public String protocol(MessageExt request) {
    return null;
  }

  @Nullable
  @Override
  public String protocolVersion(MessageExt request) {
    return null;
  }

  @Nullable
  @Override
  public String url(MessageExt request) {
    return null;
  }

  @Nullable
  @Override
  public String conversationId(MessageExt request) {
    return null;
  }

  @Nullable
  @Override
  public Long messagePayloadSize(MessageExt request) {
    byte[] body = request.getBody();
    return body == null ? null : (long) body.length;
  }

  @Nullable
  @Override
  public Long messagePayloadCompressedSize(MessageExt request) {
    return null;
  }

  @Nullable
  @Override
  public String messageId(MessageExt request, @Nullable Void unused) {
    return request.getMsgId();
  }
}
