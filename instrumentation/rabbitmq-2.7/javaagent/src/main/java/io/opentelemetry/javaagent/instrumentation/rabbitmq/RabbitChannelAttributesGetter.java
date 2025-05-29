/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

enum RabbitChannelAttributesGetter implements MessagingAttributesGetter<ChannelAndMethod, Void> {
  INSTANCE;

  @Override
  public String getSystem(ChannelAndMethod channelAndMethod) {
    return "rabbitmq";
  }

  @Nullable
  @Override
  public String getDestination(ChannelAndMethod channelAndMethod) {
    return null;
  }

  @Nullable
  @Override
  public String getDestinationTemplate(ChannelAndMethod channelAndMethod) {
    return null;
  }

  @Override
  public boolean isTemporaryDestination(ChannelAndMethod channelAndMethod) {
    return false;
  }

  @Override
  public boolean isAnonymousDestination(ChannelAndMethod channelAndMethod) {
    return false;
  }

  @Nullable
  @Override
  public String getConversationId(ChannelAndMethod channelAndMethod) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessageBodySize(ChannelAndMethod channelAndMethod) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessageEnvelopeSize(ChannelAndMethod channelAndMethod) {
    return null;
  }

  @Nullable
  @Override
  public String getMessageId(ChannelAndMethod channelAndMethod, @Nullable Void unused) {
    return null;
  }

  @Nullable
  @Override
  public String getClientId(ChannelAndMethod channelAndMethod) {
    return null;
  }

  @Nullable
  @Override
  public Long getBatchMessageCount(ChannelAndMethod channelAndMethod, @Nullable Void unused) {
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
