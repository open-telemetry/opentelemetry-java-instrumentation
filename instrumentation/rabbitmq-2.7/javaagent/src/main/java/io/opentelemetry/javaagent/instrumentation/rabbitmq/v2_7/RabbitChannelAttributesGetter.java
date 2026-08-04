/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7.DeliveredMessages.DeliveredMessage;
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
    DeliveredMessage message = settledMessage(channelAndMethod);
    return message != null ? message.getDestination() : null;
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
    DeliveredMessage message = settledMessage(channelAndMethod);
    return message != null && message.isAnonymousDestination();
  }

  /**
   * Returns the message that a {@code basicAck}, {@code basicNack} or {@code basicReject} call
   * settles, or {@code null} for any other method or when the delivery is no longer remembered.
   */
  @Nullable
  private static DeliveredMessage settledMessage(ChannelAndMethod channelAndMethod) {
    Long deliveryTag = channelAndMethod.getDeliveryTag();
    if (deliveryTag == null) {
      return null;
    }
    return DeliveredMessages.get(channelAndMethod.getChannel(), deliveryTag);
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
}
