/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1;

import com.couchbase.client.core.Core;
import com.couchbase.client.core.env.SeedNode;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.CouchbaseServerTarget;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;

// Targets are keyed by core because clusters with different targets can share an environment. Weak
// keys scope entries to the cluster lifecycle. Through 3.2, the seed set bridges target parsing to
// core construction.
public class CouchbaseServerTargets {

  private static final Cache<Set<SeedNode>, CouchbaseServerTarget> seedNodeTargets = Cache.weak();
  private static final Cache<Core, CouchbaseServerTarget> coreTargets = Cache.weak();

  public static void registerSeedNodes(
      Set<SeedNode> seedNodes, @Nullable CouchbaseServerTarget target) {
    if (target != null) {
      seedNodeTargets.put(seedNodes, target);
    }
  }

  public static void register(Core core, @Nullable CouchbaseServerTarget target) {
    if (target != null) {
      coreTargets.put(core, target);
    }
  }

  public static void registerFromSeedNodes(Core core, @Nullable Set<SeedNode> seedNodes) {
    if (seedNodes != null) {
      CouchbaseServerTarget target = seedNodeTargets.get(seedNodes);
      register(core, target != null ? target : target(seedNodes));
    }
  }

  @Nullable
  static CouchbaseServerTarget target(Set<SeedNode> seedNodes) {
    CouchbaseServerTarget.Builder target = CouchbaseServerTarget.builder();
    for (SeedNode seedNode : seedNodes) {
      if (seedNode == null) {
        target.addSeed(null, 0);
      } else {
        Optional<Integer> kvPort = seedNode.kvPort();
        Optional<Integer> clusterManagerPort = seedNode.clusterManagerPort();
        if (!kvPort.isPresent() && !clusterManagerPort.isPresent()) {
          target.addSeed(seedNode.address(), 0);
        } else {
          if (kvPort.isPresent()) {
            target.addSeed(seedNode.address(), kvPort.get());
          }
          if (clusterManagerPort.isPresent() && !clusterManagerPort.equals(kvPort)) {
            target.addSeed(seedNode.address(), clusterManagerPort.get());
          }
        }
      }
    }
    return target.build();
  }

  @Nullable
  public static CouchbaseServerTarget get(@Nullable Core core) {
    return core == null ? null : coreTargets.get(core);
  }

  private CouchbaseServerTargets() {}
}
