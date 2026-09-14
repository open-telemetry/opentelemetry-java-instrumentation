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
 * <p>One holder is published per client, before its metadata is first read, and it resolves in
 * place. {@code VirtualField} offers no compare-and-set, so the state that concurrent sends share —
 * the read throttle and the id — is held here instead: a second write could otherwise replace an
 * already resolved id with a pending one.
 *
 * <p>A client gets {@link #UNAVAILABLE} if reflection cannot reach its {@code Metadata}, which is
 * terminal, or {@link #of(Metadata)} if it can. The latter carries no id until the broker reports
 * one, and those reads are rate limited rather than capped: a slow broker must not permanently
 * suppress the attribute.
 */
final class KafkaClusterId {

  // copied from MessagingIncubatingAttributes
  static final AttributeKey<String> ATTRIBUTE_KEY =
      AttributeKey.stringKey("messaging.kafka.cluster.id");

  // Prevents retrying reflection on clients that can't provide a cluster id.
  static final KafkaClusterId UNAVAILABLE = new KafkaClusterId(null);

  // Smallest gap between two metadata reads for one client: Metadata.fetch() locks the instance
  // shared with the Kafka network thread, so its cost must not scale with span rate.
  // Package-private so the test can wait out one interval.
  static final long RETRY_INTERVAL_NANOS = MILLISECONDS.toNanos(100);

  @Nullable private final Metadata metadata;
  // Non-null only when a Metadata is present: nanoTime before which no further read is allowed.
  @Nullable private final AtomicLong nextReadNanos;
  @Nullable private volatile String clusterId;

  private KafkaClusterId(@Nullable Metadata metadata) {
    this.metadata = metadata;
    // Allow the first read immediately.
    this.nextReadNanos = metadata == null ? null : new AtomicLong(System.nanoTime());
  }

  static KafkaClusterId of(Metadata metadata) {
    return new KafkaClusterId(metadata);
  }

  @Nullable
  Metadata metadata() {
    return metadata;
  }

  @Nullable
  String clusterId() {
    return clusterId;
  }

  void resolve(String id) {
    clusterId = id;
  }

  /**
   * Returns true if this holder may read the broker metadata now, claiming the next slot. False
   * once the id is known, and for {@link #UNAVAILABLE}, which holds no {@link Metadata}. Never
   * latches false permanently, so a late broker response still resolves the cluster id.
   */
  boolean shouldReadMetadataNow() {
    if (nextReadNanos == null || clusterId != null) {
      return false;
    }
    long now = System.nanoTime();
    long next = nextReadNanos.get();
    // Subtraction, not comparison: nanoTime() is allowed to wrap around.
    return now - next >= 0 && nextReadNanos.compareAndSet(next, now + RETRY_INTERVAL_NANOS);
  }
}
