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

  // a channel can have any number of deliveries outstanding at once, and an application that is
  // slow to settle them, or that never settles them at all, would grow this map for the lifetime
  // of the channel, so the number of remembered deliveries has to be capped; when deliveries are
  // evicted the settle span that would have covered them doesn't get a destination or a batch
  // message count, because those have to describe every settled message
  private static final int CAPACITY = 1000;

  private static final VirtualField<Channel, DeliveredMessages> FIELD =
      VirtualField.find(Channel.class, DeliveredMessages.class);

  private final BoundedMap messagesByDeliveryTag = new BoundedMap();

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
    boolean incomplete = false;
    synchronized (messages) {
      if (multiple) {
        // a multiple settle covers every outstanding delivery from the start of the channel, so
        // whatever was evicted is part of what it settles; the flag is consumed here so that the
        // settles that follow, which can only cover deliveries remembered since, stay accurate
        incomplete = messages.messagesByDeliveryTag.consumeEvicted();
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
        // a single settle resolves exactly one delivery, so it is either remembered or not; the
        // eviction flag is deliberately left alone for a later multiple settle
        DeliveredMessage message = messages.messagesByDeliveryTag.remove(deliveryTag);
        if (message != null) {
          settled.add(message);
        }
      }
    }
    return SettledMessages.of(settled, incomplete);
  }

  /**
   * Forgets every outstanding delivery on a channel, as {@code basicRecover} and {@code
   * basicRecoverAsync} do: they requeue all unacknowledged deliveries, which the broker then
   * redelivers under new, higher delivery tags. Without this the old tags would linger and a later
   * multiple settle, which covers every tag up to the one it names, would settle them a second
   * time.
   */
  static void clear(Channel channel) {
    DeliveredMessages messages = FIELD.get(channel);
    if (messages == null) {
      return;
    }
    synchronized (messages) {
      // this forgets the eviction flag too: every remembered delivery was unacknowledged, so
      // whatever was evicted is requeued along with them, and the deliveries that follow, which are
      // all remembered from the start, are the only ones a later multiple settle can cover
      messages.messagesByDeliveryTag.clear();
    }
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

    private boolean evicted;

    @Override
    protected boolean removeEldestEntry(Map.Entry<Long, DeliveredMessage> eldest) {
      if (size() <= CAPACITY) {
        return false;
      }
      evicted = true;
      return true;
    }

    @Override
    public void clear() {
      super.clear();
      evicted = false;
    }

    /** Returns and clears whether any delivery has been forgotten since the last call. */
    boolean consumeEvicted() {
      boolean result = evicted;
      evicted = false;
      return result;
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

    private static SettledMessages of(List<DeliveredMessage> messages, boolean incomplete) {
      // when deliveries have been forgotten the settled messages are only a subset of what was
      // actually settled, and attributes that have to describe every settled message, as well as
      // their count, would be wrong
      if (messages.isEmpty() || incomplete) {
        return EMPTY;
      }
      DeliveredMessage first = messages.get(0);
      String destination = first.destination;
      boolean anonymousDestination = first.anonymousDestination;
      String routingKey = first.routingKey;
      for (DeliveredMessage message : messages) {
        // each attribute is aggregated on its own, and one that doesn't apply to every settled
        // message is not reported at all
        if (!Objects.equals(destination, message.destination)) {
          destination = null;
        }
        if (anonymousDestination != message.anonymousDestination) {
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
