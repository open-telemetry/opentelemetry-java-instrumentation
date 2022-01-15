/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmq;

import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import org.apache.rocketmq.common.message.MessageExt;

class RockerMqConsumerAttributeExtractor extends MessagingAttributesExtractor<MessageExt, Void> {
  @Override
  public MessageOperation operation() {
    return MessageOperation.PROCESS;
  }

  @Override
  protected String system(MessageExt request) {
    return "rocketmq";
  }

  @Override
  protected String destinationKind(MessageExt request) {
    return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
  }

  @Override
  protected String destination(MessageExt request) {
    return request.getTopic();
  }

  @Override
  protected boolean temporaryDestination(MessageExt request) {
    return false;
  }

  @Nullable
  @Override
  protected String protocol(MessageExt request) {
    return null;
  }

  @Nullable
  @Override
  protected String protocolVersion(MessageExt request) {
    return null;
  }

  @Nullable
  @Override
  protected String url(MessageExt request) {
    return null;
  }

  @Nullable
  @Override
  protected String conversationId(MessageExt request) {
    return null;
  }

  @Nullable
  @Override
  protected Long messagePayloadSize(MessageExt request) {
    byte[] body = request.getBody();
    return body == null ? null : (long) body.length;
  }

  @Nullable
  @Override
  protected Long messagePayloadCompressedSize(MessageExt request) {
    return null;
  }

  @Nullable
  @Override
  protected String messageId(MessageExt request, @Nullable Void unused) {
    return request.getMsgId();
  }
}
