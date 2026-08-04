/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7.DeliveredMessages.SettledMessages;
import java.util.List;
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
    if (channelAndMethod.isPublish()) {
      return emitStableMessagingSemconv()
          ? RabbitInstrumenterHelper.producerDestinationName(
              channelAndMethod.getExchange(), channelAndMethod.getRoutingKey())
          : RabbitInstrumenterHelper.normalizeExchangeName(channelAndMethod.getExchange());
    }
    SettledMessages messages = channelAndMethod.getSettledMessages();
    return messages != null ? messages.getDestination() : null;
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
    if (!emitStableMessagingSemconv()) {
      return false;
    }
    if (channelAndMethod.isPublish()) {
      return RabbitInstrumenterHelper.isDefaultExchange(channelAndMethod.getExchange())
          && RabbitInstrumenterHelper.isGeneratedQueueName(channelAndMethod.getRoutingKey());
    }
    SettledMessages messages = channelAndMethod.getSettledMessages();
    return messages != null && messages.isAnonymousDestination();
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
    if (!channelAndMethod.isMultipleSettle()) {
      return null;
    }
    SettledMessages messages = channelAndMethod.getSettledMessages();
    // the count is only known for deliveries that are still remembered
    return messages == null || messages.getCount() == 0 ? null : (long) messages.getCount();
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
}
