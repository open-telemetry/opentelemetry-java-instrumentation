/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7;

import static io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7.RabbitInstrumenterHelper.consumerDestinationName;
import static io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7.RabbitInstrumenterHelper.isGeneratedQueueName;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.annotation.Nullable;

/**
 * Remembers where the messages delivered on a channel came from, so that the {@code basicAck},
 * {@code basicNack} and {@code basicReject} spans, which only receive a delivery tag, can report
 * the destination of the message that they settle.
 */
final class DeliveredMessages {

  // an application using automatic acknowledgement never settles anything, so the number of
  // remembered deliveries has to be capped; when a delivery is evicted its settle span simply
  // doesn't get a destination
  private static final int CAPACITY = 1000;

  private static final VirtualField<Channel, DeliveredMessages> FIELD =
      VirtualField.find(Channel.class, DeliveredMessages.class);

  private final Cache<Long, DeliveredMessage> messagesByDeliveryTag = Cache.bounded(CAPACITY);

  static void record(Channel channel, Envelope envelope, String queue) {
    getOrCreate(channel)
        .messagesByDeliveryTag
        .put(
            envelope.getDeliveryTag(),
            new DeliveredMessage(
                consumerDestinationName(envelope.getExchange(), envelope.getRoutingKey(), queue),
                isGeneratedQueueName(queue)));
  }

  @Nullable
  static DeliveredMessage get(Channel channel, long deliveryTag) {
    DeliveredMessages messages = FIELD.get(channel);
    return messages == null ? null : messages.messagesByDeliveryTag.get(deliveryTag);
  }

  private static DeliveredMessages getOrCreate(Channel channel) {
    DeliveredMessages messages = FIELD.get(channel);
    return messages != null ? messages : create(channel);
  }

  private static synchronized DeliveredMessages create(Channel channel) {
    DeliveredMessages messages = FIELD.get(channel);
    if (messages == null) {
      messages = new DeliveredMessages();
      FIELD.set(channel, messages);
    }
    return messages;
  }

  private DeliveredMessages() {}

  static final class DeliveredMessage {
    @Nullable private final String destination;
    private final boolean anonymousDestination;

    DeliveredMessage(@Nullable String destination, boolean anonymousDestination) {
      this.destination = destination;
      this.anonymousDestination = anonymousDestination;
    }

    @Nullable
    String getDestination() {
      return destination;
    }

    boolean isAnonymousDestination() {
      return anonymousDestination;
    }
  }
}
