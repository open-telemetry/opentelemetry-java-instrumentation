/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

enum RabbitChannelAttributesGetter implements MessagingAttributesGetter<ChannelAndMethod, Void> {
  INSTANCE;

  @Override
  public String system(ChannelAndMethod channelAndMethod) {
    return "rabbitmq";
  }

  @Override
  public String destinationKind(ChannelAndMethod channelAndMethod) {
    return SemanticAttributes.MessagingDestinationKindValues.QUEUE;
  }

  @Nullable
  @Override
  public String destination(ChannelAndMethod channelAndMethod) {
    return null;
  }

  @Override
  public boolean temporaryDestination(ChannelAndMethod channelAndMethod) {
    return false;
  }

  @Nullable
  @Override
  public String protocol(ChannelAndMethod channelAndMethod) {
    return null;
  }

  @Nullable
  @Override
  public String protocolVersion(ChannelAndMethod channelAndMethod) {
    return null;
  }

  @Nullable
  @Override
  public String url(ChannelAndMethod channelAndMethod) {
    return null;
  }

  @Nullable
  @Override
  public String conversationId(ChannelAndMethod channelAndMethod) {
    return null;
  }

  @Nullable
  @Override
  public Long messagePayloadSize(ChannelAndMethod channelAndMethod) {
    return null;
  }

  @Nullable
  @Override
  public Long messagePayloadCompressedSize(ChannelAndMethod channelAndMethod) {
    return null;
  }

  @Nullable
  @Override
  public String messageId(ChannelAndMethod channelAndMethod, @Nullable Void unused) {
    return null;
  }
}
