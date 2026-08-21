/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

final class RabbitChannelAttributesGetter
    implements MessagingAttributesGetter<ChannelAndMethod, Void> {

  @Override
  public String getSystem(ChannelAndMethod channelAndMethod) {
    return "rabbitmq";
  }

  @Nullable
  @Override
  public String getDestination(ChannelAndMethod channelAndMethod) {
    if (!channelAndMethod.isPublish()) {
      return null;
    }
    return emitStableMessagingSemconv()
        ? RabbitInstrumenterHelper.producerDestinationName(
            channelAndMethod.getExchange(), channelAndMethod.getRoutingKey())
        : RabbitInstrumenterHelper.normalizeExchangeName(channelAndMethod.getExchange());
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
    return emitStableMessagingSemconv()
        && channelAndMethod.isPublish()
        && RabbitInstrumenterHelper.isDefaultExchange(channelAndMethod.getExchange())
        && RabbitInstrumenterHelper.isGeneratedQueueName(channelAndMethod.getRoutingKey());
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
        return singletonList(value.toString());
      }
    }
    return emptyList();
  }

  @Override
  public Collection<String> getMessageHeaderNames(ChannelAndMethod channelAndMethod) {
    Map<String, Object> headers = channelAndMethod.getHeaders();
    return headers == null ? emptyList() : new ArrayList<>(headers.keySet());
  }
}
