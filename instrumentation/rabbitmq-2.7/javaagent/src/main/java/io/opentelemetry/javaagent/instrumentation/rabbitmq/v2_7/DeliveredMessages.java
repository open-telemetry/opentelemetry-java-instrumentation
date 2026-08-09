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
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
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

  // whether the channel has been put into transaction mode, in which the settlements it makes are
  // only durable once they are committed
  private boolean transactional;

  // the delivery tags removed by the settles that a txRollback would undo
  private final TagRanges pendingTags = new TagRanges();

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
   *
   * <p>The result also carries the delivery tags that were removed, so that the caller can mark
   * exactly those as forgotten if the settle fails.
   */
  static SettledMessages settle(Channel channel, long deliveryTag, boolean multiple) {
    DeliveredMessages messages = FIELD.get(channel);
    if (messages == null) {
      return SettledMessages.EMPTY;
    }

    List<DeliveredMessage> settled = new ArrayList<>();
    boolean incomplete = false;
    TagRanges removedTags = new TagRanges();
    synchronized (messages) {
      if (multiple) {
        // a multiple settle covers every outstanding delivery up to and including its delivery
        // tag, so the deliveries forgotten in that range are part of what it settles; they are
        // consumed here so that the settles that follow, which cover only what is left, stay
        // accurate, and they count as removed so that a failure puts them back
        TagRanges forgottenTags = messages.messagesByDeliveryTag.consumeForgottenUpTo(deliveryTag);
        incomplete = !forgottenTags.isEmpty();
        removedTags.addAll(forgottenTags);
        Iterator<Map.Entry<Long, DeliveredMessage>> iterator =
            messages.messagesByDeliveryTag.entrySet().iterator();
        while (iterator.hasNext()) {
          Map.Entry<Long, DeliveredMessage> entry = iterator.next();
          if (deliveryTag == 0 || entry.getKey() <= deliveryTag) {
            settled.add(entry.getValue());
            removedTags.add(entry.getKey(), entry.getKey());
            iterator.remove();
          }
        }
      } else {
        // a single settle resolves exactly one delivery, so it is either remembered, or forgotten
        // while the broker still has it, in which case settling it retires the forgotten delivery
        DeliveredMessage message = messages.messagesByDeliveryTag.remove(deliveryTag);
        boolean forgotten = messages.messagesByDeliveryTag.consumeForgottenTag(deliveryTag);
        if (message != null) {
          settled.add(message);
        }
        if (message != null || forgotten) {
          removedTags.add(deliveryTag, deliveryTag);
        }
      }
      if (messages.transactional) {
        messages.pendingTags.addAll(removedTags);
      }
    }
    return SettledMessages.of(settled, incomplete, removedTags);
  }

  /** Puts a channel into transaction mode, as {@code txSelect} does. */
  static void selectTransaction(Channel channel) {
    // a channel that has not delivered anything yet still has to be remembered as transactional,
    // because the deliveries that follow can be settled and rolled back within this transaction
    DeliveredMessages messages = getOrCreate(channel);
    synchronized (messages) {
      messages.transactional = true;
    }
  }

  /**
   * Commits a transaction and starts the next one: the settlements made in the transaction that
   * just ended are durable, and only the settlements that follow can still be rolled back.
   */
  static void commitTransaction(Channel channel) {
    DeliveredMessages messages = getOrCreate(channel);
    synchronized (messages) {
      messages.transactional = true;
      messages.clearPending();
    }
  }

  /**
   * Records that deliveries that are outstanding at the broker are no longer remembered, so that a
   * later multiple settle covering them doesn't report a destination or a batch message count that
   * describes only part of what it settles.
   *
   * <p>The deliveries are not put back because the delivery tags they were remembered under are
   * only valid until the channel is recovered, at which point the broker starts numbering
   * deliveries from one again and restored entries would be attributed to unrelated messages.
   */
  static void markForgotten(Channel channel, TagRanges tags) {
    DeliveredMessages messages = FIELD.get(channel);
    if (messages == null) {
      return;
    }
    synchronized (messages) {
      messages.messagesByDeliveryTag.markForgotten(tags);
    }
  }

  /**
   * Records that the deliveries removed by the settles made in the transaction that a {@code
   * txRollback} just undid are outstanding at the broker again while they are no longer remembered.
   */
  static void markPendingForgotten(Channel channel) {
    DeliveredMessages messages = FIELD.get(channel);
    if (messages == null) {
      return;
    }
    synchronized (messages) {
      messages.messagesByDeliveryTag.markForgotten(messages.pendingTags);
      messages.clearPending();
    }
  }

  private void clearPending() {
    pendingTags.clear();
  }

  /**
   * Forgets every outstanding delivery on a channel when it is recovered. {@code basicRecover},
   * {@code basicRecoverAsync}, and automatic connection recovery requeue all unacknowledged
   * deliveries, so the old entries must not be included in a later multiple settle.
   */
  static void clear(Channel channel) {
    DeliveredMessages messages = FIELD.get(channel);
    if (messages == null) {
      return;
    }
    synchronized (messages) {
      // this forgets the settled deliveries too: every remembered delivery was unacknowledged, so
      // whatever was forgotten is requeued along with them, and the deliveries that follow, which
      // are all remembered from the start, are the only ones a later multiple settle can cover
      messages.messagesByDeliveryTag.clear();
      messages.clearPending();
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

  static final class TagRanges implements Serializable {

    private static final long serialVersionUID = 1L;

    private final TreeMap<Long, Long> ranges = new TreeMap<>();
    private boolean unknown;

    void add(long lowestTag, long highestTag) {
      if (highestTag == 0 || unknown) {
        return;
      }

      Map.Entry<Long, Long> lower = ranges.floorEntry(lowestTag);
      if (lower != null
          && (lower.getValue() == Long.MAX_VALUE || lower.getValue() + 1 >= lowestTag)) {
        lowestTag = lower.getKey();
        highestTag = Math.max(highestTag, lower.getValue());
        ranges.remove(lower.getKey());
      }

      Map.Entry<Long, Long> higher = ranges.ceilingEntry(lowestTag);
      while (higher != null
          && (highestTag == Long.MAX_VALUE || higher.getKey() <= highestTag + 1)) {
        highestTag = Math.max(highestTag, higher.getValue());
        ranges.remove(higher.getKey());
        higher = ranges.ceilingEntry(lowestTag);
      }
      ranges.put(lowestTag, highestTag);

      // Keep state bounded even when an application leaves an unbounded number of disjoint
      // deliveries outstanding. Unknown state conservatively suppresses aggregate attributes until
      // a settle covering every outstanding tag or a channel recovery resets it.
      if (ranges.size() > CAPACITY) {
        ranges.clear();
        unknown = true;
      }
    }

    void addAll(TagRanges other) {
      if (other.unknown) {
        ranges.clear();
        unknown = true;
        return;
      }
      for (Map.Entry<Long, Long> range : other.ranges.entrySet()) {
        add(range.getKey(), range.getValue());
      }
    }

    TagRanges removeUpTo(long deliveryTag) {
      TagRanges removed = new TagRanges();
      if (unknown) {
        removed.unknown = true;
        if (deliveryTag == 0) {
          clear();
        }
        return removed;
      }

      Iterator<Map.Entry<Long, Long>> iterator = ranges.entrySet().iterator();
      while (iterator.hasNext()) {
        Map.Entry<Long, Long> range = iterator.next();
        if (deliveryTag != 0 && range.getKey() > deliveryTag) {
          break;
        }
        iterator.remove();
        if (deliveryTag == 0 || range.getValue() <= deliveryTag) {
          removed.add(range.getKey(), range.getValue());
        } else {
          removed.add(range.getKey(), deliveryTag);
          ranges.put(deliveryTag + 1, range.getValue());
          break;
        }
      }
      return removed;
    }

    boolean remove(long deliveryTag) {
      if (unknown) {
        return true;
      }
      Map.Entry<Long, Long> range = ranges.floorEntry(deliveryTag);
      if (range == null || range.getValue() < deliveryTag) {
        return false;
      }

      ranges.remove(range.getKey());
      if (range.getKey() < deliveryTag) {
        add(range.getKey(), deliveryTag - 1);
      }
      if (deliveryTag < range.getValue()) {
        add(deliveryTag + 1, range.getValue());
      }
      return true;
    }

    boolean isEmpty() {
      return !unknown && ranges.isEmpty();
    }

    void clear() {
      ranges.clear();
      unknown = false;
    }
  }

  private static final class BoundedMap extends LinkedHashMap<Long, DeliveredMessage> {

    private static final long serialVersionUID = 1L;

    // delivery tags that were remembered and are no longer, while the deliveries themselves are
    // still outstanding at the broker
    private final TagRanges forgottenTags = new TagRanges();

    @Override
    protected boolean removeEldestEntry(Map.Entry<Long, DeliveredMessage> eldest) {
      if (size() <= CAPACITY) {
        return false;
      }
      forgottenTags.add(eldest.getKey(), eldest.getKey());
      return true;
    }

    @Override
    public void clear() {
      super.clear();
      forgottenTags.clear();
    }

    void markForgotten(TagRanges tags) {
      forgottenTags.addAll(tags);
    }

    /**
     * Returns whether a multiple settle up to and including {@code deliveryTag}, where {@code 0}
     * covers every outstanding delivery, settles any forgotten delivery, and forgets the ones it
     * covers.
     */
    TagRanges consumeForgottenUpTo(long deliveryTag) {
      return forgottenTags.removeUpTo(deliveryTag);
    }

    /**
     * Returns whether a single settle of {@code deliveryTag} settles a forgotten delivery, and
     * forgets it.
     */
    boolean consumeForgottenTag(long deliveryTag) {
      return forgottenTags.remove(deliveryTag);
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

    static final SettledMessages EMPTY = new SettledMessages(null, false, null, 0, new TagRanges());

    @Nullable private final String destination;
    private final boolean anonymousDestination;
    @Nullable private final String routingKey;
    private final int count;
    private final TagRanges removedTags;

    private SettledMessages(
        @Nullable String destination,
        boolean anonymousDestination,
        @Nullable String routingKey,
        int count,
        TagRanges removedTags) {
      this.destination = destination;
      this.anonymousDestination = anonymousDestination;
      this.routingKey = routingKey;
      this.count = count;
      this.removedTags = removedTags;
    }

    private static SettledMessages of(
        List<DeliveredMessage> messages, boolean incomplete, TagRanges removedTags) {
      // when deliveries have been forgotten the settled messages are only a subset of what was
      // actually settled, and attributes that have to describe every settled message, as well as
      // their count, would be wrong
      if (messages.isEmpty() || incomplete) {
        return new SettledMessages(null, false, null, 0, removedTags);
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
      return new SettledMessages(
          destination, anonymousDestination, routingKey, messages.size(), removedTags);
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

    TagRanges getRemovedTags() {
      return removedTags;
    }
  }
}
