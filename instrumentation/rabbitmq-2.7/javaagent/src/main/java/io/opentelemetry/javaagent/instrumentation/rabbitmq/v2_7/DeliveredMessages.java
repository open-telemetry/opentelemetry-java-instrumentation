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

  // whether the channel has been put into transaction mode, in which the settlements it makes are
  // only durable once they are committed
  private boolean transactional;

  // the range of delivery tags removed by the settles that a txRollback, or a failure of the
  // settle itself, would undo; a transactional channel accumulates them until the transaction
  // ends, any other channel only ever holds the settle that is in flight, and a tag of zero, which
  // no delivery ever uses, means that there is nothing to undo
  private long pendingLowestTag;
  private long pendingHighestTag;

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
   * <p>The result also carries the range of delivery tags that were removed, so that the caller can
   * mark exactly those as forgotten if the settle fails.
   */
  static SettledMessages settle(Channel channel, long deliveryTag, boolean multiple) {
    DeliveredMessages messages = FIELD.get(channel);
    if (messages == null) {
      return SettledMessages.EMPTY;
    }

    List<DeliveredMessage> settled = new ArrayList<>();
    boolean incomplete = false;
    long lowestRemovedTag = 0;
    long highestRemovedTag = 0;
    synchronized (messages) {
      if (multiple) {
        // a multiple settle covers every outstanding delivery up to and including its delivery
        // tag, so the deliveries forgotten in that range are part of what it settles; they are
        // consumed here so that the settles that follow, which cover only what is left, stay
        // accurate, and they count as removed so that a failure puts them back
        long forgottenLowestTag = messages.messagesByDeliveryTag.getForgottenLowestTag();
        long forgottenHighestTag = messages.messagesByDeliveryTag.getForgottenHighestTag();
        incomplete = messages.messagesByDeliveryTag.consumeForgottenUpTo(deliveryTag);
        if (incomplete) {
          lowestRemovedTag = forgottenLowestTag;
          highestRemovedTag =
              deliveryTag == 0 || forgottenHighestTag <= deliveryTag
                  ? forgottenHighestTag
                  : deliveryTag;
        }
        Iterator<Map.Entry<Long, DeliveredMessage>> iterator =
            messages.messagesByDeliveryTag.entrySet().iterator();
        while (iterator.hasNext()) {
          Map.Entry<Long, DeliveredMessage> entry = iterator.next();
          if (deliveryTag == 0 || entry.getKey() <= deliveryTag) {
            settled.add(entry.getValue());
            if (lowestRemovedTag == 0 || entry.getKey() < lowestRemovedTag) {
              lowestRemovedTag = entry.getKey();
            }
            if (entry.getKey() > highestRemovedTag) {
              highestRemovedTag = entry.getKey();
            }
            iterator.remove();
          }
        }
      } else {
        // a single settle resolves exactly one delivery, so it is either remembered, or forgotten
        // while the broker still has it, in which case settling it retires the forgotten delivery
        DeliveredMessage message = messages.messagesByDeliveryTag.remove(deliveryTag);
        if (message != null) {
          settled.add(message);
          lowestRemovedTag = deliveryTag;
          highestRemovedTag = deliveryTag;
        } else if (messages.messagesByDeliveryTag.consumeForgottenTag(deliveryTag)) {
          lowestRemovedTag = deliveryTag;
          highestRemovedTag = deliveryTag;
        }
      }
      if (messages.transactional) {
        messages.addPending(lowestRemovedTag, highestRemovedTag);
      }
    }
    return SettledMessages.of(settled, incomplete, lowestRemovedTag, highestRemovedTag);
  }

  /**
   * Starts a new transaction on a channel, as {@code txSelect} and {@code txCommit} do: the
   * settlements made in the transaction that just ended, if there was one, are durable, and only
   * the settlements that follow can still be rolled back.
   */
  static void startTransaction(Channel channel) {
    // a channel that has not delivered anything yet still has to be remembered as transactional,
    // because the deliveries that follow can be settled and rolled back within this transaction
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
  static void markForgotten(Channel channel, long lowestTag, long highestTag) {
    DeliveredMessages messages = FIELD.get(channel);
    if (messages == null) {
      return;
    }
    synchronized (messages) {
      messages.messagesByDeliveryTag.markForgotten(lowestTag, highestTag);
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
      messages.messagesByDeliveryTag.markForgotten(
          messages.pendingLowestTag, messages.pendingHighestTag);
      messages.clearPending();
    }
  }

  private void addPending(long lowestTag, long highestTag) {
    if (highestTag == 0) {
      return;
    }
    if (pendingHighestTag == 0 || lowestTag < pendingLowestTag) {
      pendingLowestTag = lowestTag;
    }
    if (highestTag > pendingHighestTag) {
      pendingHighestTag = highestTag;
    }
  }

  private void clearPending() {
    pendingLowestTag = 0;
    pendingHighestTag = 0;
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

  private static final class BoundedMap extends LinkedHashMap<Long, DeliveredMessage> {

    private static final long serialVersionUID = 1L;

    // the range of delivery tags that were remembered and are no longer, while the deliveries
    // themselves are still outstanding at the broker; a tag of zero, which no delivery ever uses,
    // means that nothing has been forgotten
    private long forgottenLowestTag;
    private long forgottenHighestTag;

    @Override
    protected boolean removeEldestEntry(Map.Entry<Long, DeliveredMessage> eldest) {
      if (size() <= CAPACITY) {
        return false;
      }
      markForgotten(eldest.getKey(), eldest.getKey());
      return true;
    }

    @Override
    public void clear() {
      super.clear();
      forgottenLowestTag = 0;
      forgottenHighestTag = 0;
    }

    void markForgotten(long lowestTag, long highestTag) {
      if (highestTag == 0) {
        return;
      }
      if (forgottenHighestTag == 0 || lowestTag < forgottenLowestTag) {
        forgottenLowestTag = lowestTag;
      }
      if (highestTag > forgottenHighestTag) {
        forgottenHighestTag = highestTag;
      }
    }

    long getForgottenLowestTag() {
      return forgottenLowestTag;
    }

    long getForgottenHighestTag() {
      return forgottenHighestTag;
    }

    /**
     * Returns whether a multiple settle up to and including {@code deliveryTag}, where {@code 0}
     * covers every outstanding delivery, settles any forgotten delivery, and forgets the ones it
     * covers.
     */
    boolean consumeForgottenUpTo(long deliveryTag) {
      if (forgottenHighestTag == 0) {
        return false;
      }
      if (deliveryTag != 0 && forgottenLowestTag > deliveryTag) {
        return false;
      }
      if (deliveryTag == 0 || forgottenHighestTag <= deliveryTag) {
        forgottenLowestTag = 0;
        forgottenHighestTag = 0;
      } else {
        // deliveries above the settled tag are still forgotten; which ones is no longer known, so
        // everything above it is treated as forgotten until a settle covers the whole range
        forgottenLowestTag = deliveryTag + 1;
      }
      return true;
    }

    /**
     * Returns whether a single settle of {@code deliveryTag} settles a forgotten delivery, and
     * forgets it.
     */
    boolean consumeForgottenTag(long deliveryTag) {
      if (forgottenHighestTag == 0
          || deliveryTag < forgottenLowestTag
          || deliveryTag > forgottenHighestTag) {
        return false;
      }
      if (forgottenLowestTag == forgottenHighestTag) {
        forgottenLowestTag = 0;
        forgottenHighestTag = 0;
      } else if (deliveryTag == forgottenLowestTag) {
        forgottenLowestTag = deliveryTag + 1;
      } else if (deliveryTag == forgottenHighestTag) {
        forgottenHighestTag = deliveryTag - 1;
      }
      // a tag inside the range can't narrow it, because which deliveries within it were forgotten
      // is no longer known
      return true;
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

    static final SettledMessages EMPTY = new SettledMessages(null, false, null, 0, 0, 0);

    @Nullable private final String destination;
    private final boolean anonymousDestination;
    @Nullable private final String routingKey;
    private final int count;
    private final long lowestRemovedTag;
    private final long highestRemovedTag;

    private SettledMessages(
        @Nullable String destination,
        boolean anonymousDestination,
        @Nullable String routingKey,
        int count,
        long lowestRemovedTag,
        long highestRemovedTag) {
      this.destination = destination;
      this.anonymousDestination = anonymousDestination;
      this.routingKey = routingKey;
      this.count = count;
      this.lowestRemovedTag = lowestRemovedTag;
      this.highestRemovedTag = highestRemovedTag;
    }

    private static SettledMessages of(
        List<DeliveredMessage> messages,
        boolean incomplete,
        long lowestRemovedTag,
        long highestRemovedTag) {
      // when deliveries have been forgotten the settled messages are only a subset of what was
      // actually settled, and attributes that have to describe every settled message, as well as
      // their count, would be wrong
      if (messages.isEmpty() || incomplete) {
        return new SettledMessages(null, false, null, 0, lowestRemovedTag, highestRemovedTag);
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
          destination,
          anonymousDestination,
          routingKey,
          messages.size(),
          lowestRemovedTag,
          highestRemovedTag);
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

    /**
     * Returns the lowest delivery tag that the settle removed from the remembered deliveries,
     * {@code 0} when it removed none.
     */
    long getLowestRemovedTag() {
      return lowestRemovedTag;
    }

    /**
     * Returns the highest delivery tag that the settle removed from the remembered deliveries,
     * {@code 0} when it removed none.
     */
    long getHighestRemovedTag() {
      return highestRemovedTag;
    }
  }
}
