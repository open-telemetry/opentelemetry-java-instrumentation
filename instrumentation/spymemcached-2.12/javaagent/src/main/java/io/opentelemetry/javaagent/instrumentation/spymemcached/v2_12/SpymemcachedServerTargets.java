/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTargetBuilder;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.net.InetSocketAddress;
import java.util.List;
import javax.annotation.Nullable;
import net.spy.memcached.MemcachedConnection;

public class SpymemcachedServerTargets {

  private static final int DEFAULT_PORT = 11211;
  private static final int MAX_ENDPOINT_COUNT = 5;
  private static final VirtualField<MemcachedConnection, DbServerTarget> CONFIGURED_TARGETS =
      VirtualField.find(MemcachedConnection.class, DbServerTarget.class);

  public static void capture(
      @Nullable MemcachedConnection connection, @Nullable List<InetSocketAddress> nodes) {
    if (connection == null) {
      return;
    }
    CONFIGURED_TARGETS.set(connection, create(nodes));
  }

  @Nullable
  static DbServerTarget create(@Nullable List<InetSocketAddress> nodes) {
    if (nodes == null) {
      return null;
    }
    DbServerTargetBuilder builder =
        DbServerTarget.builder(DEFAULT_PORT).setSorted(false).setMaxEndpoints(MAX_ENDPOINT_COUNT);
    for (InetSocketAddress node : nodes) {
      builder.addEndpoint(node);
    }
    return builder.build();
  }

  @Nullable
  static DbServerTarget get(MemcachedConnection connection) {
    return CONFIGURED_TARGETS.get(connection);
  }

  private SpymemcachedServerTargets() {}
}
