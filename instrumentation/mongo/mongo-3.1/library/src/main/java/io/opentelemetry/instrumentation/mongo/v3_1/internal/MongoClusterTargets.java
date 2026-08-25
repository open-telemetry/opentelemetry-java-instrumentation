/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1.internal;

import com.mongodb.connection.ClusterId;
import com.mongodb.connection.ConnectionDescription;
import com.mongodb.connection.ConnectionId;
import com.mongodb.connection.ServerId;
import com.mongodb.event.CommandStartedEvent;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import javax.annotation.Nullable;

/**
 * The configured target of every MongoDB cluster the instrumentation has seen, keyed by the cluster
 * identity that each command event carries.
 *
 * <p>A cluster is registered while it is being constructed, which happens before any command can
 * run, so a command event always sees the target its client was built with. Every command event
 * reaches its cluster through the server that answered it, whose identity is created from the
 * cluster's own {@link ClusterId}. Clusters are held weakly, so a target is released together with
 * the client that configured it.
 *
 * <p>Registration is skipped when the client is not built through an instrumented cluster
 * constructor, for example when the library instrumentation is used on its own. A command event
 * that finds no target is then described by the server that answered it.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class MongoClusterTargets {

  private static final Cache<ClusterId, MongoServerTarget> targets = Cache.weak();

  /** Records the target {@code clusterId} was configured with, ignoring an unknown one. */
  public static void register(ClusterId clusterId, @Nullable MongoServerTarget target) {
    if (target != null) {
      targets.put(clusterId, target);
    }
  }

  /**
   * The target the client that issued {@code event} was configured with, or {@code null} when it is
   * unknown.
   */
  @Nullable
  public static MongoServerTarget get(CommandStartedEvent event) {
    ConnectionDescription connectionDescription = event.getConnectionDescription();
    if (connectionDescription == null) {
      return null;
    }
    ConnectionId connectionId = connectionDescription.getConnectionId();
    if (connectionId == null) {
      return null;
    }
    ServerId serverId = connectionId.getServerId();
    if (serverId == null) {
      return null;
    }
    return targets.get(serverId.getClusterId());
  }

  private MongoClusterTargets() {}
}
