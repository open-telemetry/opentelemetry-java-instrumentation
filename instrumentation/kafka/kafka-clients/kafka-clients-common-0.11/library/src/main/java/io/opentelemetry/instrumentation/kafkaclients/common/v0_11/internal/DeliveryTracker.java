/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.kafka.common.TopicPartition;

/**
 * Identifies the source of a delivery and remembers which of its deliveries a process operation
 * failed to handle.
 *
 * <p>When a process operation fails and the consumer seeks back to the failed offset, the same
 * record is delivered again. A failed delivery stays pending here until a later process operation
 * for it succeeds, so that {@code messaging.client.consumed.messages} does not count the redelivery
 * as a newly consumed message.
 *
 * <p>One tracker belongs to one consumer and is stable for the lifetime of that consumer, so that
 * two consumers reading the same offset are tracked independently. It does not reference the
 * consumer, so attaching it to consumer records does not keep the consumer alive.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class DeliveryTracker {

  private static final int MAX_PENDING_FAILED_OPERATIONS = 1024;

  // bounded so that a consumer that never recovers cannot grow this without limit
  private final Deque<PendingFailure> pendingFailures = new ArrayDeque<>();

  synchronized boolean isPendingFailed(DeliveryKey deliveryKey) {
    return findPendingFailure(deliveryKey) != null;
  }

  synchronized DeliveryState start(List<DeliveryKey> deliveryKeys) {
    PendingFailure[] pendingFailures = new PendingFailure[deliveryKeys.size()];
    for (int i = 0; i < deliveryKeys.size(); i++) {
      pendingFailures[i] = findPendingFailure(deliveryKeys.get(i));
    }
    return new DeliveryState(this, deliveryKeys, pendingFailures);
  }

  private synchronized void end(DeliveryState state, boolean successful) {
    if (!successful) {
      removePendingFailures(state.deliveryKeys);
      PendingFailure pendingFailure = PendingFailure.create(state.deliveryKeys);
      if (pendingFailure.isEmpty()) {
        return;
      }
      pendingFailures.addFirst(pendingFailure);
      if (pendingFailures.size() > MAX_PENDING_FAILED_OPERATIONS) {
        pendingFailures.removeLast();
      }
      return;
    }

    for (int i = 0; i < state.deliveryKeys.size(); i++) {
      DeliveryKey deliveryKey = state.deliveryKeys.get(i);
      PendingFailure pendingFailure = state.pendingFailures[i];
      if (pendingFailure != null && pendingFailure == findPendingFailure(deliveryKey)) {
        pendingFailure.remove(deliveryKey);
        if (pendingFailure.isEmpty()) {
          pendingFailures.remove(pendingFailure);
        }
      }
    }
  }

  @Nullable
  private PendingFailure findPendingFailure(DeliveryKey deliveryKey) {
    for (PendingFailure pendingFailure : pendingFailures) {
      if (pendingFailure.contains(deliveryKey)) {
        return pendingFailure;
      }
    }
    return null;
  }

  private void removePendingFailures(List<DeliveryKey> deliveryKeys) {
    Iterator<PendingFailure> iterator = pendingFailures.iterator();
    while (iterator.hasNext()) {
      PendingFailure pendingFailure = iterator.next();
      for (DeliveryKey deliveryKey : deliveryKeys) {
        pendingFailure.remove(deliveryKey);
      }
      if (pendingFailure.isEmpty()) {
        iterator.remove();
      }
    }
  }

  static final class DeliveryKey {
    private final TopicPartition topicPartition;
    private final long offset;

    DeliveryKey(String topic, int partition, long offset) {
      this.topicPartition = new TopicPartition(topic, partition);
      this.offset = offset;
    }
  }

  static final class DeliveryState {
    private final DeliveryTracker deliveryTracker;
    private final List<DeliveryKey> deliveryKeys;
    private final PendingFailure[] pendingFailures;

    private DeliveryState(
        DeliveryTracker deliveryTracker,
        List<DeliveryKey> deliveryKeys,
        PendingFailure[] pendingFailures) {
      this.deliveryTracker = deliveryTracker;
      this.deliveryKeys = deliveryKeys;
      this.pendingFailures = pendingFailures;
    }

    boolean wasPendingFailed(int index) {
      return pendingFailures[index] != null;
    }

    void end(boolean successful) {
      deliveryTracker.end(this, successful);
    }
  }

  /** Stores one failed operation as offset ranges, so its batch size does not affect capacity. */
  private static final class PendingFailure {
    private final Map<TopicPartition, List<OffsetRange>> rangesByPartition;

    private PendingFailure(Map<TopicPartition, List<OffsetRange>> rangesByPartition) {
      this.rangesByPartition = rangesByPartition;
    }

    private static PendingFailure create(List<DeliveryKey> deliveryKeys) {
      Map<TopicPartition, List<Long>> offsetsByPartition = new HashMap<>();
      for (DeliveryKey deliveryKey : deliveryKeys) {
        List<Long> offsets = offsetsByPartition.get(deliveryKey.topicPartition);
        if (offsets == null) {
          offsets = new ArrayList<>();
          offsetsByPartition.put(deliveryKey.topicPartition, offsets);
        }
        offsets.add(deliveryKey.offset);
      }

      Map<TopicPartition, List<OffsetRange>> rangesByPartition = new HashMap<>();
      for (Map.Entry<TopicPartition, List<Long>> entry : offsetsByPartition.entrySet()) {
        List<Long> offsets = entry.getValue();
        Collections.sort(offsets);
        List<OffsetRange> ranges = new ArrayList<>();
        for (long offset : offsets) {
          if (ranges.isEmpty()) {
            ranges.add(new OffsetRange(offset, offset));
            continue;
          }
          OffsetRange range = ranges.get(ranges.size() - 1);
          if (offset == range.end) {
            continue;
          }
          if (range.end != Long.MAX_VALUE && offset == range.end + 1) {
            range.end = offset;
          } else {
            ranges.add(new OffsetRange(offset, offset));
          }
        }
        rangesByPartition.put(entry.getKey(), ranges);
      }
      return new PendingFailure(rangesByPartition);
    }

    private boolean contains(DeliveryKey deliveryKey) {
      List<OffsetRange> ranges = rangesByPartition.get(deliveryKey.topicPartition);
      if (ranges == null) {
        return false;
      }
      for (OffsetRange range : ranges) {
        if (range.contains(deliveryKey.offset)) {
          return true;
        }
      }
      return false;
    }

    private void remove(DeliveryKey deliveryKey) {
      List<OffsetRange> ranges = rangesByPartition.get(deliveryKey.topicPartition);
      if (ranges == null) {
        return;
      }
      ListIterator<OffsetRange> iterator = ranges.listIterator();
      while (iterator.hasNext()) {
        OffsetRange range = iterator.next();
        if (!range.contains(deliveryKey.offset)) {
          continue;
        }
        if (range.start == range.end) {
          iterator.remove();
        } else if (deliveryKey.offset == range.start) {
          range.start++;
        } else if (deliveryKey.offset == range.end) {
          range.end--;
        } else {
          long end = range.end;
          range.end = deliveryKey.offset - 1;
          iterator.add(new OffsetRange(deliveryKey.offset + 1, end));
        }
        if (ranges.isEmpty()) {
          rangesByPartition.remove(deliveryKey.topicPartition);
        }
        return;
      }
    }

    private boolean isEmpty() {
      return rangesByPartition.isEmpty();
    }
  }

  private static final class OffsetRange {
    private long start;
    private long end;

    private OffsetRange(long start, long end) {
      this.start = start;
      this.end = end;
    }

    private boolean contains(long offset) {
      return offset >= start && offset <= end;
    }
  }
}
