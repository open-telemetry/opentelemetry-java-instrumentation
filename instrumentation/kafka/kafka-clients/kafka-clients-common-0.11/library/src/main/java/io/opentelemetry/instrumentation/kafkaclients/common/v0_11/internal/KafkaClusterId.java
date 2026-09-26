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
 * <p>One holder is published per client, before its metadata is first read, and it resolves in
 * place. {@code VirtualField} offers no compare-and-set, so the id is held here instead of being
 * written back: a second write could otherwise replace an already resolved id with a pending one.
 *
 * <p>A client gets {@link #UNAVAILABLE} if reflection cannot reach its {@code Metadata}, which is
 * terminal, or {@link #of(Metadata)} if it can. The latter carries no id until the broker reports
 * one.
 */
final class KafkaClusterId {

  // copied from MessagingIncubatingAttributes
  static final AttributeKey<String> ATTRIBUTE_KEY =
      AttributeKey.stringKey("messaging.kafka.cluster.id");

  // Prevents retrying reflection on clients that can't provide a cluster id.
  static final KafkaClusterId UNAVAILABLE = new KafkaClusterId(null);

  @Nullable private final Metadata metadata;
  @Nullable private volatile String clusterId;

  private KafkaClusterId(@Nullable Metadata metadata) {
    this.metadata = metadata;
  }

  static KafkaClusterId of(Metadata metadata) {
    return new KafkaClusterId(metadata);
  }

  /** Null only for {@link #UNAVAILABLE}, which has nothing left to read. */
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
}
