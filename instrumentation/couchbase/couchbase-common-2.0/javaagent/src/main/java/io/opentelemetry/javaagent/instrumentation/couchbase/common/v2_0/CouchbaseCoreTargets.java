/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v2_0;

import com.couchbase.client.core.ClusterFacade;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.CouchbaseServerTarget;
import javax.annotation.Nullable;

// Every object handed out by a cluster dispatches through the same core. Weak keys keep the target
// scoped to that cluster's lifetime. A missing entry lets legacy telemetry use the contacted node.
public class CouchbaseCoreTargets {

  private static final Cache<ClusterFacade, CouchbaseServerTarget> targets = Cache.weak();

  public static void register(ClusterFacade core, @Nullable CouchbaseServerTarget target) {
    if (target != null) {
      targets.put(core, target);
    }
  }

  @Nullable
  public static CouchbaseServerTarget get(@Nullable ClusterFacade core) {
    return core == null ? null : targets.get(core);
  }

  private CouchbaseCoreTargets() {}
}
