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
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class MongoClusterTargets {

  // weak keys release targets together with their clients
  private static final Cache<ClusterId, MongoServerTarget> targets = Cache.weak();

  public static void register(ClusterId clusterId, @Nullable MongoServerTarget target) {
    if (target != null) {
      targets.put(clusterId, target);
    }
  }

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
