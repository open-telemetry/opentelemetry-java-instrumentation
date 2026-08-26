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

// Targets are keyed by core because clusters with different targets can share an environment. Weak
// keys scope entries to the cluster lifecycle. Through 3.2, the seed set bridges target parsing to
// core construction.
public class CouchbaseServerTargets {

  private static final Cache<Set<?>, CouchbaseServerTarget> seedNodeTargets = Cache.weak();
  private static final Cache<Core, CouchbaseServerTarget> coreTargets = Cache.weak();

  public static void registerSeedNodes(Set<?> seedNodes, @Nullable CouchbaseServerTarget target) {
    if (target != null) {
      seedNodeTargets.put(seedNodes, target);
    }
  }

  public static void register(Core core, @Nullable CouchbaseServerTarget target) {
    if (target != null) {
      coreTargets.put(core, target);
    }
  }

  public static void registerFromSeedNodes(Core core, @Nullable Set<?> seedNodes) {
    if (seedNodes != null) {
      register(core, seedNodeTargets.get(seedNodes));
    }
  }

  @Nullable
  public static CouchbaseServerTarget get(@Nullable Core core) {
    return core == null ? null : coreTargets.get(core);
  }

  private CouchbaseServerTargets() {}
}
