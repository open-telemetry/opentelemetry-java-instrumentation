/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.net.InetSocketAddress;
import java.util.List;
import javax.annotation.Nullable;
import net.spy.memcached.MemcachedConnection;

/**
 * The single configured server of each connection the instrumentation has seen being created.
 *
 * <p>A connection factory is handed the node list a client was built with and turns it into the
 * connection that client then runs every operation through. Capturing a single node there keeps
 * later reconnects from changing the configured server. A client with several configured nodes
 * reports the node that handles each operation instead.
 */
public class SpymemcachedServerTargets {

  private static final VirtualField<MemcachedConnection, SpymemcachedServerTarget>
      CONFIGURED_TARGETS =
          VirtualField.find(MemcachedConnection.class, SpymemcachedServerTarget.class);

  /** Records the target when {@code nodes} names exactly one server. */
  public static void capture(
      @Nullable MemcachedConnection connection, @Nullable List<InetSocketAddress> nodes) {
    if (connection == null) {
      return;
    }
    CONFIGURED_TARGETS.set(connection, SpymemcachedServerTarget.create(nodes));
  }

  /**
   * The single server {@code connection} was created for, or {@code null} when the instrumentation
   * did not see it being created or its nodes did not name exactly one server.
   */
  @Nullable
  public static SpymemcachedServerTarget get(MemcachedConnection connection) {
    return CONFIGURED_TARGETS.get(connection);
  }

  private SpymemcachedServerTargets() {}
}
