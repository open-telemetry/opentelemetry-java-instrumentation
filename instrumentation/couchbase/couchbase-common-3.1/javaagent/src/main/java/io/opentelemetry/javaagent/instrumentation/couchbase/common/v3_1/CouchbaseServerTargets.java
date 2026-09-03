/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1;

import com.couchbase.client.core.Core;
import com.couchbase.client.core.env.CoreEnvironment;
import com.couchbase.client.core.env.SeedNode;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTargetBuilder;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
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

  public static void registerFromSeedNodes(
      Core core, @Nullable Set<SeedNode> seedNodes, @Nullable CoreEnvironment environment) {
    if (seedNodes != null) {
      CouchbaseServerTarget target = seedNodeTargets.get(seedNodes);
      if (target == null && environment != null) {
        target = target(seedNodes, environment.securityConfig().tlsEnabled());
      }
      register(core, target);
    }
  }

  @Nullable
  static CouchbaseServerTarget target(Set<SeedNode> seedNodes, boolean tlsEnabled) {
    DbServerTargetBuilder target =
        DbServerTarget.builder(
                CouchbaseServerTarget.defaultPort(tlsEnabled ? "couchbases" : "couchbase"))
            .setSorted(true);
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
    return CouchbaseServerTarget.direct(target.build());
  }

  private static void addSeed(
      DbServerTargetBuilder target,
      Map<String, Set<Integer>> portsByAddress,
      @Nullable String address,
      int port) {
    if (address == null) {
      target.addEndpoint(null, port > 0 ? port : -1);
      return;
    }
    Set<Integer> ports = portsByAddress.get(address);
    if (ports == null) {
      ports = new HashSet<>();
      portsByAddress.put(address, ports);
    }
    if (ports.add(port)) {
      target.addEndpoint(address, port > 0 ? port : -1);
    }
  }

  @Nullable
  public static CouchbaseServerTarget get(@Nullable Core core) {
    return core == null ? null : coreTargets.get(core);
  }

  private CouchbaseServerTargets() {}
}
