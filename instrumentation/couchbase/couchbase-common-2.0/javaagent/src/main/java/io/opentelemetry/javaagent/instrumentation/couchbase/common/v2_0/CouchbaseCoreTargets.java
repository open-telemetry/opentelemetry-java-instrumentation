/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v2_0;

import com.couchbase.client.core.ClusterFacade;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.CouchbaseServerTarget;
import javax.annotation.Nullable;

/**
 * The configured target of every Couchbase 2.x cluster the instrumentation has seen, keyed by the
 * core the cluster dispatches through.
 *
 * <p>A cluster registers its target while it is being constructed, which is the last point at which
 * the connection string is still the one the client was built with. Every bucket, bucket manager
 * and cluster manager the cluster hands out dispatches through that same core, so an operation
 * always sees the target of the client that issued it. Cores are held weakly, so a target is
 * released together with the cluster that configured it.
 *
 * <p>Registration is skipped when a core is not built through an instrumented cluster constructor.
 * An operation that finds no target is then described by the node that answered it, on the drivers
 * that report one.
 */
public class CouchbaseCoreTargets {

  private static final Cache<ClusterFacade, CouchbaseServerTarget> targets = Cache.weak();

  /** Records the target {@code core} was configured with, ignoring an unknown one. */
  public static void register(ClusterFacade core, @Nullable CouchbaseServerTarget target) {
    if (target != null) {
      targets.put(core, target);
    }
  }

  /** The target {@code core} was configured with, or {@code null} when it is unknown. */
  @Nullable
  public static CouchbaseServerTarget get(@Nullable ClusterFacade core) {
    return core == null ? null : targets.get(core);
  }

  private CouchbaseCoreTargets() {}
}
