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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
  void nodesIncludeAtMostFirstFiveAfterSorting() {
    RedisServerTarget target =
        JedisServerTargets.ofNodes(
            asList(
                new HostAndPort("node6", 6379),
                new HostAndPort("node5", 6379),
                new HostAndPort("node4", 6379),
                new HostAndPort("node3", 6379),
                new HostAndPort("node2", 6379),
                new HostAndPort("node1", 6379)));

    assertThat(target.getAddress()).isEqualTo("node1,node2,node3,node4,node5");
  }

  @Test
  void oneNodeKeepsItsPort() {
    RedisServerTarget target =
        JedisServerTargets.ofNodes(singletonList(new HostAndPort("node1", 7000)));

    assertThat(target.getAddress()).isEqualTo("node1");
    assertThat(target.getPort()).isEqualTo(7000);
  }

  @Test
  void nodeListWithUnsupportedMemberFailsClosed() {
    assertThat(JedisServerTargets.ofNodes(asList(new HostAndPort("node1", 7000), "unsupported")))
        .isNull();
  }

  @Test
  void duplicateNodesFromAListArePreserved() {
    RedisServerTarget target =
        JedisServerTargets.ofNodes(
            asList(new HostAndPort("node1", 7000), new HostAndPort("node1", 7000)));

    assertThat(target.getAddress()).isEqualTo("node1:7000,node1:7000");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void shardsKeepTheirOrder() {
    RedisServerTarget target =
        JedisServerTargets.ofShards(
            asList(new HostAndPort("shard2", 6380), new HostAndPort("shard1", 6379)));

    assertThat(target.getAddress()).isEqualTo("shard2:6380,shard1:6379");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void shardsWithSharedNonDefaultPortKeepPortsInline() {
    RedisServerTarget target =
        JedisServerTargets.ofShards(
            asList(new HostAndPort("shard1", 6380), new HostAndPort("shard2", 6380)));

    assertThat(target.getAddress()).isEqualTo("shard1:6380,shard2:6380");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void sentinelsAreSortedAndKeepDuplicatesAndMasterSuffix() {
    RedisServerTarget target =
        JedisServerTargets.ofSentinels(
            "mymaster",
            asList(
                new HostAndPort("sentinel2", 26380),
                new HostAndPort("sentinel1", 26379),
                new HostAndPort("sentinel2", 26380)));

    assertThat(target.getAddress())
        .isEqualTo("sentinel1:26379,sentinel2:26380,sentinel2:26380/mymaster");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void sentinelListWithNullMemberFailsClosed() {
    assertThat(
            JedisServerTargets.ofSentinels(
                "mymaster", asList(new HostAndPort("sentinel1", 26379), null)))
        .isNull();
  }

  @Test
  void sentinelListWithUnsupportedMemberFailsClosedWithoutConversion() {
    assertThat(
            JedisServerTargets.ofSentinels(
                "mymaster", asList(new HostAndPort("sentinel1", 26379), unconvertibleMember())))
        .isNull();
  }

  @Test
  void sentinelIpv6AddressKeepsItsPort() {
    RedisServerTarget target =
        JedisServerTargets.ofSentinels(
            "mymaster", singletonList(new HostAndPort("2001:db8::1", 26379)));

    assertThat(target.getAddress()).isEqualTo("[2001:db8::1]:26379/mymaster");
    assertThat(target.getPort()).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"2001:db8::1", "2001:db8::"})
  void stringSentinelIpv6AddressKeepsItsPort(String host) {
    RedisServerTarget target =
        JedisServerTargets.ofSentinels("mymaster", singletonList(host + ":26379"));

    assertThat(target.getAddress()).isEqualTo("[" + host + "]:26379/mymaster");
    assertThat(target.getPort()).isNull();
  }

  private static Object unconvertibleMember() {
    return new Object() {
      @Override
      public String toString() {
        throw new IllegalStateException("must not convert unsupported member");
      }
    };
  }

  @Test
  void portlessStringSentinelIpv6AddressStaysUnbracketed() {
    RedisServerTarget target =
        JedisServerTargets.ofSentinels("mymaster", singletonList("2001:db8::1"));

    assertThat(target.getAddress()).isEqualTo("2001:db8::1/mymaster");
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

    assertThat(target.getAddress()).isEqualTo("sentinel1:26379,sentinel2:26380/mymaster");
  }

  @Test
  void noNodes() {
    assertThat(JedisServerTargets.ofNodes(null)).isNull();
    assertThat(JedisServerTargets.ofNodes(emptyList())).isNull();
  }
}
