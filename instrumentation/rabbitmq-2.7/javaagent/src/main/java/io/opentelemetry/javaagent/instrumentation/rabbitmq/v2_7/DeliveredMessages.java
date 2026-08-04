/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7;

import static io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7.RabbitInstrumenterHelper.consumerDestinationName;
import static io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7.RabbitInstrumenterHelper.isGeneratedQueueName;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Remembers where the messages delivered on a channel came from, so that the {@code basicAck},
 * {@code basicNack} and {@code basicReject} spans, which only receive a delivery tag, can report
 * the destination of the messages that they settle.
 */
final class DeliveredMessages {

  // an application using automatic acknowledgement never settles anything, so the number of
  // remembered deliveries has to be capped; when a delivery is evicted its settle span simply
  // doesn't get a destination
  private static final int CAPACITY = 1000;

  private static final VirtualField<Channel, DeliveredMessages> FIELD =
      VirtualField.find(Channel.class, DeliveredMessages.class);

  private final Map<Long, DeliveredMessage> messagesByDeliveryTag = new BoundedMap();

  static void record(Channel channel, Envelope envelope, String queue) {
    DeliveredMessage message =
        new DeliveredMessage(
            consumerDestinationName(envelope.getExchange(), envelope.getRoutingKey(), queue),
            isGeneratedQueueName(queue),
            envelope.getRoutingKey());
    DeliveredMessages messages = getOrCreate(channel);
    synchronized (messages) {
      messages.messagesByDeliveryTag.put(envelope.getDeliveryTag(), message);
    }
  }

  /**
   * Removes and returns the deliveries settled by a {@code basicAck}, {@code basicNack} or {@code
   * basicReject} call. When {@code multiple} is set, every delivery up to and including {@code
   * deliveryTag} is settled; a delivery tag of {@code 0} then settles every outstanding delivery.
   */
  static SettledMessages settle(Channel channel, long deliveryTag, boolean multiple) {
    DeliveredMessages messages = FIELD.get(channel);
    if (messages == null) {
      return SettledMessages.EMPTY;
    }

    List<DeliveredMessage> settled = new ArrayList<>();
    synchronized (messages) {
      if (multiple) {
        Iterator<Map.Entry<Long, DeliveredMessage>> iterator =
            messages.messagesByDeliveryTag.entrySet().iterator();
        while (iterator.hasNext()) {
          Map.Entry<Long, DeliveredMessage> entry = iterator.next();
          if (deliveryTag == 0 || entry.getKey() <= deliveryTag) {
            settled.add(entry.getValue());
            iterator.remove();
          }
        }
      } else {
        DeliveredMessage message = messages.messagesByDeliveryTag.remove(deliveryTag);
        if (message != null) {
          settled.add(message);
        }
      }
    }
    return SettledMessages.of(settled);
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

  private static final class BoundedMap extends LinkedHashMap<Long, DeliveredMessage> {

    private static final long serialVersionUID = 1L;

    @Override
    protected boolean removeEldestEntry(Map.Entry<Long, DeliveredMessage> eldest) {
      return size() > CAPACITY;
    }
  }

  private static final class DeliveredMessage {
    @Nullable private final String destination;
    private final boolean anonymousDestination;
    @Nullable private final String routingKey;

    DeliveredMessage(
        @Nullable String destination, boolean anonymousDestination, @Nullable String routingKey) {
      this.destination = destination;
      this.anonymousDestination = anonymousDestination;
      this.routingKey = routingKey;
    }
  }

  /**
   * The deliveries settled by a single {@code basicAck}, {@code basicNack} or {@code basicReject}.
   */
  static final class SettledMessages {

    static final SettledMessages EMPTY = new SettledMessages(null, false, null, 0);

    @Nullable private final String destination;
    private final boolean anonymousDestination;
    @Nullable private final String routingKey;
    private final int count;

    private SettledMessages(
        @Nullable String destination,
        boolean anonymousDestination,
        @Nullable String routingKey,
        int count) {
      this.destination = destination;
      this.anonymousDestination = anonymousDestination;
      this.routingKey = routingKey;
      this.count = count;
    }

    private static SettledMessages of(List<DeliveredMessage> messages) {
      if (messages.isEmpty()) {
        return EMPTY;
      }
      DeliveredMessage first = messages.get(0);
      String destination = first.destination;
      boolean anonymousDestination = first.anonymousDestination;
      String routingKey = first.routingKey;
      for (DeliveredMessage message : messages) {
        // attributes that don't apply to every settled message are not reported at all
        if (!Objects.equals(destination, message.destination)) {
          destination = null;
          anonymousDestination = false;
        }
        if (!Objects.equals(routingKey, message.routingKey)) {
          routingKey = null;
        }
      }
      return new SettledMessages(destination, anonymousDestination, routingKey, messages.size());
    }

    @Nullable
    String getDestination() {
      return destination;
    }

    boolean isAnonymousDestination() {
      return anonymousDestination;
    }

    @Nullable
    String getRoutingKey() {
      return routingKey;
    }

    int getCount() {
      return count;
    }
  }
}
