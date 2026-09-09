/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import io.opentelemetry.api.common.AttributeKey;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import org.apache.kafka.clients.Metadata;

/**
 * Dedicated {@code VirtualField} value type (rather than {@code String}) so the per-instance cache
 * cannot collide with other instrumentations that attach a {@code String}-typed {@code
 * VirtualField} to the same {@code Producer}/{@code Consumer} classes — {@code VirtualField} is
 * keyed by target type + value type.
 *
 * <p>Lifecycle: {@link #UNAVAILABLE} (reflection cannot reach the metadata at all) → {@link
 * #of(Metadata)} (pending: broker response not yet received) → {@link #resolved(String)} (cluster
 * id known; hot path returns it directly without acquiring the Metadata lock). The pending state is
 * never converted to {@link #UNAVAILABLE}: a slow broker must not permanently suppress the
 * attribute, so pending reads are rate limited instead of capped.
 */
final class KafkaClusterId {

  // copied from MessagingIncubatingAttributes
  static final AttributeKey<String> ATTRIBUTE_KEY =
      AttributeKey.stringKey("messaging.kafka.cluster.id");

  // Prevents retrying reflection on clients that can't provide a cluster id.
  static final KafkaClusterId UNAVAILABLE = new KafkaClusterId(null, null);

  // Smallest gap between two metadata reads for the same pending client. Metadata.fetch()
  // synchronizes on the Metadata instance shared with the Kafka network thread, so its cost must
  // not scale with span rate. Package-private so the test can wait out one interval.
  static final long RETRY_INTERVAL_NANOS = MILLISECONDS.toNanos(100);

  @Nullable final Metadata metadata;
  @Nullable final String clusterId;
  // Non-null only in the pending state: nanoTime before which no further read is allowed.
  @Nullable private final AtomicLong nextReadNanos;

  private KafkaClusterId(
      @Nullable Metadata metadata, @Nullable String clusterId, @Nullable AtomicLong nextReadNanos) {
    this.metadata = metadata;
    this.clusterId = clusterId;
    this.nextReadNanos = nextReadNanos;
  }

  private KafkaClusterId(@Nullable Metadata metadata, @Nullable String clusterId) {
    this(metadata, clusterId, null);
  }

  static KafkaClusterId of(Metadata metadata) {
    // Allow the first read immediately.
    return new KafkaClusterId(metadata, null, new AtomicLong(System.nanoTime()));
  }

  static KafkaClusterId resolved(String clusterId) {
    return new KafkaClusterId(null, clusterId);
  }

  /**
   * Returns true if this pending entry may read the broker metadata now, claiming the next slot.
   * Always false for the terminal states, which hold no {@link Metadata}. Never latches false
   * permanently, so a late broker response still resolves the cluster id.
   */
  boolean shouldReadMetadataNow() {
    if (nextReadNanos == null) {
      return false;
    }
    long now = System.nanoTime();
    long next = nextReadNanos.get();
    // Subtraction, not comparison: nanoTime() is allowed to wrap around.
    return now - next >= 0 && nextReadNanos.compareAndSet(next, now + RETRY_INTERVAL_NANOS);
  }
}
