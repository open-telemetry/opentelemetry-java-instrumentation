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
 */
final class KafkaClusterId {

  static final AttributeKey<String> ATTRIBUTE_KEY =
      AttributeKey.stringKey("messaging.kafka.cluster.id");

  /**
   * Sentinel cached for clients that can never resolve a cluster id (wrong client type, or no
   * {@code metadata} field), so the reflective walk is not retried on every span.
   */
  static final KafkaClusterId UNAVAILABLE = new KafkaClusterId(null);

  // The Metadata field is final in KafkaProducer/KafkaConsumer, so this cached reference never
  // goes stale. Cluster id is read fresh each span via Metadata.fetch() rather than cached as a
  // String, so it stays current even after a cluster replacement at the same broker addresses.
  @Nullable final Metadata metadata;

  private KafkaClusterId(@Nullable Metadata metadata) {
    this.metadata = metadata;
  }

  static KafkaClusterId of(Metadata metadata) {
    return new KafkaClusterId(metadata);
  }
}
