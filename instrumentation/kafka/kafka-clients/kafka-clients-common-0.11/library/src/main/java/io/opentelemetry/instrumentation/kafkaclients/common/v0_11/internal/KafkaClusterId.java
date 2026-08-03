/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import io.opentelemetry.api.common.AttributeKey;
import javax.annotation.Nullable;
import org.apache.kafka.clients.Metadata;

/**
 * Dedicated {@code VirtualField} value type (rather than {@code String}) so the per-instance cache
 * cannot collide with other instrumentations that attach a {@code String}-typed {@code
 * VirtualField} to the same {@code Producer}/{@code Consumer} classes — {@code VirtualField} is
 * keyed by target type + value type.
 *
 * <p>Lifecycle: {@link #UNAVAILABLE} (reflection failed) → {@link #of(Metadata)} (pending: broker
 * response not yet received) → {@link #resolved(String)} (cluster id known; hot path returns it
 * directly without acquiring the Metadata lock).
 */
final class KafkaClusterId {

  // copied from MessagingIncubatingAttributes
  static final AttributeKey<String> ATTRIBUTE_KEY =
      AttributeKey.stringKey("messaging.kafka.cluster.id");

  /**
   * Sentinel for clients that can never resolve a cluster id (wrong client type, or no {@code
   * metadata} field); prevents retrying the reflective walk on every span.
   */
  static final KafkaClusterId UNAVAILABLE = new KafkaClusterId(null, null);

  /**
   * Non-null while the cluster id is still pending (broker response not yet received). Cleared once
   * the id is resolved and this entry is replaced with {@link #resolved(String)}.
   */
  @Nullable final Metadata metadata;

  /**
   * Non-null once the cluster id has been successfully fetched. When present, returned directly on
   * the hot path — no lock acquisition on {@code Metadata} is required.
   */
  @Nullable final String clusterId;

  private KafkaClusterId(@Nullable Metadata metadata, @Nullable String clusterId) {
    this.metadata = metadata;
    this.clusterId = clusterId;
  }

  /** Creates a pending entry that holds the {@code Metadata} reference for deferred resolution. */
  static KafkaClusterId of(Metadata metadata) {
    return new KafkaClusterId(metadata, null);
  }

  /** Creates a fully-resolved entry. Hot-path reads return {@code clusterId} with no lock. */
  static KafkaClusterId resolved(String clusterId) {
    return new KafkaClusterId(null, clusterId);
  }
}
