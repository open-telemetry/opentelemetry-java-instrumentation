/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

public class RabbitChannelAttributesExtractor
    extends MessagingAttributesExtractor<ChannelAndMethod, Void> {
  @Override
  public MessageOperation operation() {
    return MessageOperation.SEND;
  }

  @Override
  protected String system(ChannelAndMethod channelAndMethod) {
    return "rabbitmq";
  }

  @Override
  protected String destinationKind(ChannelAndMethod channelAndMethod) {
    return SemanticAttributes.MessagingDestinationKindValues.QUEUE;
  }

  @Nullable
  @Override
  protected String destination(ChannelAndMethod channelAndMethod) {
    return null;
  }

  @Override
  protected boolean temporaryDestination(ChannelAndMethod channelAndMethod) {
    return false;
  }

  @Nullable
  @Override
  protected String protocol(ChannelAndMethod channelAndMethod) {
    return null;
  }

  @Nullable
  @Override
  protected String protocolVersion(ChannelAndMethod channelAndMethod) {
    return null;
  }

  @Nullable
  @Override
  protected String url(ChannelAndMethod channelAndMethod) {
    return null;
  }

  @Nullable
  @Override
  protected String conversationId(ChannelAndMethod channelAndMethod) {
    return null;
  }

  @Nullable
  @Override
  protected Long messagePayloadSize(ChannelAndMethod channelAndMethod) {
    return null;
  }

  @Nullable
  @Override
  protected Long messagePayloadCompressedSize(ChannelAndMethod channelAndMethod) {
    return null;
  }

  @Nullable
  @Override
  protected String messageId(ChannelAndMethod channelAndMethod, @Nullable Void unused) {
    return null;
  }
}
