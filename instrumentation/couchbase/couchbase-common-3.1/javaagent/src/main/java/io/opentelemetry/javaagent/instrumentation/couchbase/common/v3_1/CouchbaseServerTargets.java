/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1;

import com.couchbase.client.core.Core;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.CouchbaseServerTarget;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * The configured target of every Couchbase 3.x core the instrumentation has seen.
 *
 * <p>A core records its target while it is being constructed, which is the last point at which the
 * connection string is still the one the client was built with. The target is held against the core
 * rather than the environment, because several clusters can share one environment while each is
 * pointed at a deployment of its own. Cores are held weakly, so a target is released together with
 * the cluster that configured it.
 *
 * <p>The drivers up to 3.2 do not hand the connection string to the core, so the target is read
 * where the driver turns the connection string into seed nodes and is then held against that seed
 * node set until the core it belongs to is built.
 *
 * <p>Registration is skipped when a core is not built from a connection string. An operation that
 * finds no target then carries no server address.
 */
public final class CouchbaseServerTargets {

  private static final Cache<Set<?>, CouchbaseServerTarget> seedNodeTargets = Cache.weak();
  private static final Cache<Core, CouchbaseServerTarget> coreTargets = Cache.weak();

  /** Records the target the seed nodes in {@code seedNodes} were resolved from. */
  public static void registerSeedNodes(Set<?> seedNodes, @Nullable CouchbaseServerTarget target) {
    if (target != null) {
      seedNodeTargets.put(seedNodes, target);
    }
  }

  /** Records the target {@code core} was configured with, ignoring an unknown one. */
  public static void register(Core core, @Nullable CouchbaseServerTarget target) {
    if (target != null) {
      coreTargets.put(core, target);
    }
  }

  /** Records the target of the connection string {@code seedNodes} were resolved from. */
  public static void registerFromSeedNodes(Core core, @Nullable Set<?> seedNodes) {
    if (seedNodes != null) {
      register(core, seedNodeTargets.get(seedNodes));
    }
  }

  /** The target {@code core} was configured with, or {@code null} when it is unknown. */
  @Nullable
  public static CouchbaseServerTarget get(@Nullable Core core) {
    return core == null ? null : coreTargets.get(core);
  }

  private CouchbaseServerTargets() {}
}
