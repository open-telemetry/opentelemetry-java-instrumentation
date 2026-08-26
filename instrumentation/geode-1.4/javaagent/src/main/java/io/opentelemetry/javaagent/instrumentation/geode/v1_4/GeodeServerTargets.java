/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode.v1_4;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.client.Pool;
import org.apache.geode.cache.client.PoolFactory;
import org.apache.geode.cache.client.PoolManager;

// Public because advice may be inlined into Geode classes in a different package.
public class GeodeServerTargets {

  private static final VirtualField<PoolFactory, GeodeServerTarget.Builder> CONFIGURED_TARGETS =
      VirtualField.find(PoolFactory.class, GeodeServerTarget.Builder.class);
  private static final VirtualField<Pool, GeodeServerTarget> POOL_TARGETS =
      VirtualField.find(Pool.class, GeodeServerTarget.class);

  public static void addServer(PoolFactory poolFactory, @Nullable String host, int port) {
    builder(poolFactory).addServer(host, port);
  }

  public static void addLocator(PoolFactory poolFactory, @Nullable String host, int port) {
    builder(poolFactory).addLocator(host, port);
  }

  public static void setServerGroup(PoolFactory poolFactory, @Nullable String serverGroup) {
    builder(poolFactory).setServerGroup(serverGroup);
  }

  public static void reset(PoolFactory poolFactory) {
    builder(poolFactory).reset();
  }

  public static void copyConfiguration(PoolFactory poolFactory, Pool sourcePool) {
    GeodeServerTarget.Builder builder = builder(poolFactory);
    builder.reset();
    builder.setServerGroup(sourcePool.getServerGroup());
    for (InetSocketAddress server : sourcePool.getServers()) {
      builder.addServer(server.getHostString(), server.getPort());
    }
    for (InetSocketAddress locator : sourcePool.getLocators()) {
      builder.addLocator(locator.getHostString(), locator.getPort());
    }
  }

  public static void capture(PoolFactory poolFactory, @Nullable Pool pool) {
    if (pool != null) {
      // Each pool keeps the configuration snapshot from the moment it was created.
      POOL_TARGETS.set(pool, builder(poolFactory).build());
    }
  }

  @Nullable
  public static GeodeServerTarget get(Region<?, ?> region) {
    Pool pool = PoolManager.find(region);
    return pool == null ? null : POOL_TARGETS.get(pool);
  }

  private static GeodeServerTarget.Builder builder(PoolFactory poolFactory) {
    GeodeServerTarget.Builder builder = CONFIGURED_TARGETS.get(poolFactory);
    if (builder == null) {
      builder = GeodeServerTarget.builder();
      CONFIGURED_TARGETS.set(poolFactory, builder);
    }
    return builder;
  }

  private GeodeServerTargets() {}
}
