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

/**
 * The configured target of every client pool the instrumentation has seen being created.
 *
 * <p>A pool factory collects what an operator configures, and the pool it creates is handed a
 * snapshot of that configuration. Rendering the target while the pool is being created keeps the
 * servers a pool discovers and the connections it holds at any moment out of the target, and leaves
 * an operation with nothing to do beyond looking its pool up.
 */
public final class GeodeServerTargets {

  private static final VirtualField<PoolFactory, GeodeServerTarget.Builder> CONFIGURED_TARGETS =
      VirtualField.find(PoolFactory.class, GeodeServerTarget.Builder.class);
  private static final VirtualField<Pool, GeodeServerTarget> POOL_TARGETS =
      VirtualField.find(Pool.class, GeodeServerTarget.class);

  /** Records a cache server {@code poolFactory} is being configured with. */
  public static void addServer(PoolFactory poolFactory, @Nullable String host, int port) {
    builder(poolFactory).addServer(host, port);
  }

  /** Records a locator {@code poolFactory} is being configured with. */
  public static void addLocator(PoolFactory poolFactory, @Nullable String host, int port) {
    builder(poolFactory).addLocator(host, port);
  }

  /** Records the server group {@code poolFactory} is being configured with. */
  public static void setServerGroup(PoolFactory poolFactory, @Nullable String serverGroup) {
    builder(poolFactory).setServerGroup(serverGroup);
  }

  /** Forgets the configuration {@code poolFactory} collected so far. */
  public static void reset(PoolFactory poolFactory) {
    builder(poolFactory).reset();
  }

  /** Copies the configured target from {@code sourcePool}. */
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

  /** Records the target {@code pool} was created with. */
  public static void capture(PoolFactory poolFactory, @Nullable Pool pool) {
    if (pool != null) {
      POOL_TARGETS.set(pool, builder(poolFactory).build());
    }
  }

  /**
   * The target the pool behind {@code region} was configured with, or {@code null} when the region
   * reaches no pool or its pool names no target.
   */
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
