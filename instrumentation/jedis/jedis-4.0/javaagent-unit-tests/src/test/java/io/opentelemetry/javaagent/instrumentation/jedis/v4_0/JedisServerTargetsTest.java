/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import java.util.LinkedHashSet;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.HostAndPort;

class JedisServerTargetsTest {

  @Test
  void nodesAreSorted() {
    RedisServerTarget target =
        JedisServerTargets.ofNodes(
            new LinkedHashSet<>(
                asList(new HostAndPort("node2", 7001), new HostAndPort("node1", 7000))));

    assertThat(target.getAddress()).isEqualTo("node1:7000,node2:7001");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void oneNodeKeepsItsPort() {
    RedisServerTarget target =
        JedisServerTargets.ofNodes(singletonList(new HostAndPort("node1", 7000)));

    assertThat(target.getAddress()).isEqualTo("node1");
    assertThat(target.getPort()).isEqualTo(7000);
  }

  @Test
  void nodesThatNameTheSameEndpointCollapse() {
    RedisServerTarget target =
        JedisServerTargets.ofNodes(
            asList(new HostAndPort("node1", 7000), new HostAndPort("node1", 7000)));

    assertThat(target.getAddress()).isEqualTo("node1");
    assertThat(target.getPort()).isEqualTo(7000);
  }

  @Test
  void sentinelsAreSortedAndScopedByTheirMaster() {
    RedisServerTarget target =
        JedisServerTargets.ofSentinels(
            "mymaster",
            asList(
                new HostAndPort("sentinel2", 26380),
                new HostAndPort("sentinel1", 26379),
                new HostAndPort("sentinel2", 26380)));

    assertThat(target.getAddress()).isEqualTo("sentinel1:26379/mymaster,sentinel2:26380/mymaster");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void sentinelCollectionIsFoundInConstructorArguments() {
    RedisServerTarget target =
        JedisServerTargets.ofSentinelsFromArguments(
            "mymaster",
            new Object[] {
              "mymaster",
              asList(new HostAndPort("sentinel2", 26380), new HostAndPort("sentinel1", 26379))
            });

    assertThat(target.getAddress()).isEqualTo("sentinel1:26379/mymaster,sentinel2:26380/mymaster");
  }

  @Test
  void noNodes() {
    assertThat(JedisServerTargets.ofNodes(null)).isNull();
    assertThat(JedisServerTargets.ofNodes(emptyList())).isNull();
  }
}
