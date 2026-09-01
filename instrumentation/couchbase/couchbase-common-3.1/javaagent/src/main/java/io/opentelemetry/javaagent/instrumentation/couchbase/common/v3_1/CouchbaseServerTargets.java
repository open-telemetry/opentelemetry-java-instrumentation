/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1;

import com.couchbase.client.core.Core;
import com.couchbase.client.core.env.SeedNode;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.CouchbaseServerTarget;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
    CouchbaseServerTarget.Builder target = CouchbaseServerTarget.builder("couchbase");
    Map<String, Set<Integer>> portsByAddress = new HashMap<>();
    for (SeedNode seedNode : seedNodes) {
      if (seedNode == null) {
        addSeed(target, portsByAddress, null, 0);
      } else {
        Optional<Integer> kvPort = seedNode.kvPort();
        Optional<Integer> clusterManagerPort = seedNode.clusterManagerPort();
        if (!kvPort.isPresent() && !clusterManagerPort.isPresent()) {
          addSeed(target, portsByAddress, seedNode.address(), 0);
        } else {
          if (kvPort.isPresent()) {
            addSeed(target, portsByAddress, seedNode.address(), kvPort.get());
          }
          if (clusterManagerPort.isPresent() && !clusterManagerPort.equals(kvPort)) {
            addSeed(target, portsByAddress, seedNode.address(), clusterManagerPort.get());
          }
        }
      }
    }
    return target.build();
  }

  private static void addSeed(
      CouchbaseServerTarget.Builder target,
      Map<String, Set<Integer>> portsByAddress,
      @Nullable String address,
      int port) {
    if (address == null) {
      target.addSeed(null, port);
      return;
    }
    Set<Integer> ports = portsByAddress.get(address);
    if (ports == null) {
      ports = new HashSet<>();
      portsByAddress.put(address, ports);
    }
    if (ports.add(port)) {
      target.addSeed(address, port);
    }
  }

  @Nullable
  public static CouchbaseServerTarget get(@Nullable Core core) {
    return core == null ? null : coreTargets.get(core);
  }

  private CouchbaseServerTargets() {}
}
