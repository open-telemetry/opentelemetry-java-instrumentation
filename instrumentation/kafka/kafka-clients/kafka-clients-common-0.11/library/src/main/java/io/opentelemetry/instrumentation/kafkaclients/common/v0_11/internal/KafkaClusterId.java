/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import io.opentelemetry.api.common.AttributeKey;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * suppress the attribute. The first span gets two reads, because the broker's first response
 * usually arrives between its start and its end.
 */
final class KafkaClusterId {

  // copied from MessagingIncubatingAttributes
  static final AttributeKey<String> ATTRIBUTE_KEY =
      AttributeKey.stringKey("messaging.kafka.cluster.id");

  // Prevents retrying reflection on clients that can't provide a cluster id.
  static final KafkaClusterId UNAVAILABLE = new KafkaClusterId(null);

  // Smallest gap between metadata reads for one client once its first read has been spent:
  // Metadata.fetch() locks the instance shared with the Kafka network thread, so its cost must not
  // scale with span rate. Package-private so the test can wait out one interval.
  static final long RETRY_INTERVAL_NANOS = MILLISECONDS.toNanos(100);

  @Nullable private final Metadata metadata;
  // Non-null only when a Metadata is present: nanoTime before which no further read is allowed.
  @Nullable private final AtomicLong nextReadNanos;
  // The first read does not spend the interval's slot; see shouldReadMetadataNow().
  private final AtomicBoolean firstReadPending = new AtomicBoolean(true);
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
    // The broker's first metadata response lands between a span's start and its end, and the
    // producer re-reads at end for that reason. Leaving the interval's slot unspent on the first
    // read is what lets that second read through.
    if (firstReadPending.compareAndSet(true, false)) {
      return true;
    }
    long now = System.nanoTime();
    long next = nextReadNanos.get();
    // Subtraction, not comparison: nanoTime() is allowed to wrap around.
    return now - next >= 0 && nextReadNanos.compareAndSet(next, now + RETRY_INTERVAL_NANOS);
  }
}
