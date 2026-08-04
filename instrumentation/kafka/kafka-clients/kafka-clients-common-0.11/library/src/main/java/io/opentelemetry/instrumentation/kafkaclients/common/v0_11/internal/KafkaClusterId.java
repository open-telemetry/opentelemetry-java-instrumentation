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

  // Prevents retrying reflection on clients that can't provide a cluster id.
  static final KafkaClusterId UNAVAILABLE = new KafkaClusterId(null, null);

  @Nullable final Metadata metadata;
  @Nullable final String clusterId;

  private KafkaClusterId(@Nullable Metadata metadata, @Nullable String clusterId) {
    this.metadata = metadata;
    this.clusterId = clusterId;
  }

  static KafkaClusterId of(Metadata metadata) {
    return new KafkaClusterId(metadata, null);
  }

  static KafkaClusterId resolved(String clusterId) {
    return new KafkaClusterId(null, clusterId);
  }
}
