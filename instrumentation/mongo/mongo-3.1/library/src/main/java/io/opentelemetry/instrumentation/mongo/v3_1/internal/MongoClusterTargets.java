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
import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class MongoClusterTargets {

  private static final VirtualField<ClusterId, MongoServerTarget> CLUSTER_TARGET =
      VirtualField.find(ClusterId.class, MongoServerTarget.class);

  public static void register(ClusterId clusterId, @Nullable MongoServerTarget target) {
    if (target != null) {
      CLUSTER_TARGET.set(clusterId, target);
    }
  }

  static void register(CommandStartedEvent event, @Nullable MongoServerTarget configuredTarget) {
    ClusterId clusterId = clusterId(event);
    if (clusterId != null) {
      register(clusterId, configuredTarget);
    }
  }

  @Nullable
  static MongoServerTarget get(CommandStartedEvent event) {
    ClusterId clusterId = clusterId(event);
    return clusterId == null ? null : CLUSTER_TARGET.get(clusterId);
  }

  @Nullable
  private static ClusterId clusterId(CommandStartedEvent event) {
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
    return serverId.getClusterId();
  }

  private MongoClusterTargets() {}
}
