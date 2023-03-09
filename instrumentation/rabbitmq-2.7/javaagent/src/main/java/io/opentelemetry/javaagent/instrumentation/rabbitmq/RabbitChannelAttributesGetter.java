/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

enum RabbitChannelAttributesGetter implements MessagingAttributesGetter<ChannelAndMethod, Void> {
  INSTANCE;

  @Override
  public String getSystem(ChannelAndMethod channelAndMethod) {
    return "rabbitmq";
  }

  @Override
  public String getDestinationKind(ChannelAndMethod channelAndMethod) {
    return SemanticAttributes.MessagingDestinationKindValues.QUEUE;
  }

  @Nullable
  @Override
  public String getDestination(ChannelAndMethod channelAndMethod) {
    return null;
  }

  @Override
  public boolean isTemporaryDestination(ChannelAndMethod channelAndMethod) {
    return false;
  }

  @Nullable
  @Override
  public String getConversationId(ChannelAndMethod channelAndMethod) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessagePayloadSize(ChannelAndMethod channelAndMethod) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessagePayloadCompressedSize(ChannelAndMethod channelAndMethod) {
    return null;
  }

  @Nullable
  @Override
  public String getMessageId(ChannelAndMethod channelAndMethod, @Nullable Void unused) {
    return null;
  }

  @Override
  public List<String> getMessageHeader(ChannelAndMethod channelAndMethod, String name) {
    if (channelAndMethod.getHeaders() != null) {
      Object value = channelAndMethod.getHeaders().get(name);
      if (value != null) {
        return Collections.singletonList(value.toString());
      }
    }
    return Collections.emptyList();
  }
}
