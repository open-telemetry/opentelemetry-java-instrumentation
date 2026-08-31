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

public class SpymemcachedServerTargets {

  private static final VirtualField<MemcachedConnection, SpymemcachedServerTarget>
      CONFIGURED_TARGETS =
          VirtualField.find(MemcachedConnection.class, SpymemcachedServerTarget.class);

  public static void capture(
      @Nullable MemcachedConnection connection, @Nullable List<InetSocketAddress> nodes) {
    if (connection == null) {
      return;
    }
    CONFIGURED_TARGETS.set(connection, SpymemcachedServerTarget.create(nodes));
  }

  @Nullable
  static SpymemcachedServerTarget get(MemcachedConnection connection) {
    return CONFIGURED_TARGETS.get(connection);
  }

  private SpymemcachedServerTargets() {}
}
