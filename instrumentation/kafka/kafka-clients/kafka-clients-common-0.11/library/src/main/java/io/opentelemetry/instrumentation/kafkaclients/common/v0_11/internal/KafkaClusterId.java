/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import io.opentelemetry.api.common.AttributeKey;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import org.apache.kafka.clients.Metadata;

/**
 * Dedicated {@code VirtualField} value type (rather than {@code String}) so the per-instance cache
 * cannot collide with other instrumentations that attach a {@code String}-typed {@code
 * VirtualField} to the same {@code Producer}/{@code Consumer} classes — {@code VirtualField} is
 * keyed by target type + value type.
 *
 * <p>Lifecycle: {@link #UNAVAILABLE} (reflection failed, or the broker never reported an id) →
 * {@link #of(Metadata)} (pending: broker response not yet received) → {@link #resolved(String)}
 * (cluster id known; hot path returns it directly without acquiring the Metadata lock).
 */
final class KafkaClusterId {

  // copied from MessagingIncubatingAttributes
  static final AttributeKey<String> ATTRIBUTE_KEY =
      AttributeKey.stringKey("messaging.kafka.cluster.id");

  // Prevents retrying reflection on clients that can't provide a cluster id.
  static final KafkaClusterId UNAVAILABLE = new KafkaClusterId(null, null);

  // Caps how many spans may re-read a pending client's metadata. Metadata.fetch() synchronizes on
  // the Metadata instance shared with the Kafka network thread, so retrying forever would put every
  // span behind that lock on a client whose broker never reports a cluster id.
  private static final int MAX_PENDING_ATTEMPTS = 10;

  @Nullable final Metadata metadata;
  @Nullable final String clusterId;
  // Non-null only in the pending state.
  @Nullable private final AtomicInteger pendingAttempts;

  private KafkaClusterId(
      @Nullable Metadata metadata,
      @Nullable String clusterId,
      @Nullable AtomicInteger pendingAttempts) {
    this.metadata = metadata;
    this.clusterId = clusterId;
    this.pendingAttempts = pendingAttempts;
  }

  private KafkaClusterId(@Nullable Metadata metadata, @Nullable String clusterId) {
    this(metadata, clusterId, null);
  }

  static KafkaClusterId of(Metadata metadata) {
    return new KafkaClusterId(metadata, null, new AtomicInteger());
  }

  static KafkaClusterId resolved(String clusterId) {
    return new KafkaClusterId(null, clusterId);
  }

  /** Returns true once this pending entry has used up its retry budget. */
  boolean pendingAttemptsExhausted() {
    return pendingAttempts != null && pendingAttempts.incrementAndGet() >= MAX_PENDING_ATTEMPTS;
  }
}
