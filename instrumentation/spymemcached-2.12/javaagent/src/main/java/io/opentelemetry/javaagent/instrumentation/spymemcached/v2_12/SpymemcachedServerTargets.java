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
 * The configured target of every connection the instrumentation has seen being created.
 *
 * <p>A connection factory is handed the node list a client was built with and turns it into the
 * connection that client then runs every operation through. Rendering the target there keeps the
 * nodes a connection later drops, reconnects to or picks for a single operation out of it, and
 * leaves an operation with nothing to do beyond looking its connection up.
 */
public final class SpymemcachedServerTargets {

  private static final VirtualField<MemcachedConnection, SpymemcachedServerTarget>
      CONFIGURED_TARGETS =
          VirtualField.find(MemcachedConnection.class, SpymemcachedServerTarget.class);

  /** Records the nodes {@code connection} was created for. */
  public static void capture(
      @Nullable MemcachedConnection connection, @Nullable List<InetSocketAddress> nodes) {
    if (connection == null) {
      return;
    }
    CONFIGURED_TARGETS.set(connection, SpymemcachedServerTarget.create(nodes));
  }

  /**
   * The target {@code connection} was created for, or {@code null} when the instrumentation did not
   * see it being created or its nodes named no server.
   */
  @Nullable
  public static SpymemcachedServerTarget get(MemcachedConnection connection) {
    return CONFIGURED_TARGETS.get(connection);
  }

  private SpymemcachedServerTargets() {}
}
