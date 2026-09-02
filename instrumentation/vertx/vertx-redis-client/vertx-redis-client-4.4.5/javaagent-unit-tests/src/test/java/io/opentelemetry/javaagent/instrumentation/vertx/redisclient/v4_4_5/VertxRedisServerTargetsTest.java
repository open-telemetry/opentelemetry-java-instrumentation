/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_4_5;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.vertx.redis.client.RedisClientType;
import io.vertx.redis.client.RedisClusterConnectOptions;
import io.vertx.redis.client.RedisConnectOptions;
import io.vertx.redis.client.RedisOptions;
import io.vertx.redis.client.RedisSentinelConnectOptions;
import io.vertx.redis.client.RedisStandaloneConnectOptions;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class VertxRedisServerTargetsTest {

  @Test
  void standalone() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisStandaloneConnectOptions().setConnectionString("redis://host:6379"));

    assertThat(target.getAddress()).isEqualTo("host");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void standaloneUsesTheEffectiveDefaultPort() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisStandaloneConnectOptions().setConnectionString("redis://host"));

    assertThat(target.getAddress()).isEqualTo("host");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void standaloneDropsCredentialsDatabaseAndQuery() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisStandaloneConnectOptions()
                .setConnectionString("redis://user:secret@host:6379/2?client_name=app#fragment"));

    assertThat(target.getAddress()).isEqualTo("host");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void standaloneIgnoresTheEndpointsItNeverConnectsTo() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisStandaloneConnectOptions()
                .addConnectionString("redis://host1:6379")
                .addConnectionString("redis://host2:6380"));

    assertThat(target.getAddress()).isEqualTo("host1");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void standaloneKeepsNonDefaultPortSeparate() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisStandaloneConnectOptions().setConnectionString("redis://host:6380"));

    assertThat(target.getAddress()).isEqualTo("host");
    assertThat(target.getPort()).isEqualTo(6380);
  }

  @Test
  void clusterEndpointPermutationsRenderIdentically() {
    RedisServerTarget first =
        VertxRedisServerTargets.of(
            new RedisClusterConnectOptions()
                .addConnectionString("redis://node2:7001")
                .addConnectionString("redis://node1:7000"));
    RedisServerTarget second =
        VertxRedisServerTargets.of(
            new RedisClusterConnectOptions()
                .addConnectionString("redis://node1:7000")
                .addConnectionString("redis://node2:7001"));

    assertThat(first.getAddress()).isEqualTo("node1:7000,node2:7001");
    assertThat(second.getAddress()).isEqualTo(first.getAddress());
    assertThat(first.getPort()).isNull();
  }

  @Test
  void clusterWithOneEndpointKeepsItsPort() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisClusterConnectOptions().addConnectionString("redis://node1:7000"));

    assertThat(target.getAddress()).isEqualTo("node1");
    assertThat(target.getPort()).isEqualTo(7000);
  }

  @Test
  void clusterOmitsSharedDefaultPort() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisClusterConnectOptions()
                .addConnectionString("redis://node2")
                .addConnectionString("redis://node1:6379"));

    assertThat(target.getAddress()).isEqualTo("node1,node2");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void clusterExtractsSharedNonDefaultPort() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisClusterConnectOptions()
                .addConnectionString("redis://node2:7000")
                .addConnectionString("redis://node1:7000"));

    assertThat(target.getAddress()).isEqualTo("node1:7000,node2:7000");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void clusterDropsCredentialsAndDatabase() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisClusterConnectOptions()
                .addConnectionString("redis://user:secret@node1:7000/2")
                .addConnectionString("redis://node2:7001"));

    assertThat(target.getAddress()).isEqualTo("node1:7000,node2:7001");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void clusterUsesTheEffectiveDefaultPort() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisClusterConnectOptions()
                .addConnectionString("redis://node1")
                .addConnectionString("redis://node2:7001"));

    assertThat(target.getAddress()).isEqualTo("node1:6379,node2:7001");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void clusterKeepsDuplicateEffectiveEndpoints() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisClusterConnectOptions()
                .addConnectionString("redis://node1")
                .addConnectionString("redis://node1:6379"));

    assertThat(target.getAddress()).isEqualTo("node1,node1");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void staticReplicationPreservesEndpointOrder() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new StaticReplicationRedisOptions()
                .addConnectionString("redis://z-master:6380")
                .addConnectionString("redis://a-replica:6380"));

    assertThat(target.getAddress()).isEqualTo("z-master:6380,a-replica:6380");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void staticReplicationConnectOptionsPreserveEndpointOrder() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new StaticReplicationConnectOptions()
                .addConnectionString("redis://z-master:6380")
                .addConnectionString("redis://a-replica:6380"));

    assertThat(target.getAddress()).isEqualTo("z-master:6380,a-replica:6380");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void staticReplicationWithMultipleUnixSocketsIsUnrepresentable() {
    RedisServerTarget redisOptionsTarget =
        VertxRedisServerTargets.of(
            new StaticReplicationRedisOptions()
                .addConnectionString("unix:///var/run/redis-master.sock")
                .addConnectionString("unix:///var/run/redis-replica.sock"));
    RedisServerTarget connectOptionsTarget =
        VertxRedisServerTargets.of(
            new StaticReplicationConnectOptions()
                .addConnectionString("unix:///var/run/redis-master.sock")
                .addConnectionString("unix:///var/run/redis-replica.sock"));

    assertThat(redisOptionsTarget).isNull();
    assertThat(connectOptionsTarget).isNull();
  }

  @Test
  void clusterWithMultipleUnixSocketsIsUnrepresentable() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisClusterConnectOptions()
                .addConnectionString("unix:///var/run/redis2.sock")
                .addConnectionString("unix:///var/run/redis1.sock"));

    assertThat(target).isNull();
  }

  @Test
  void clusterWithOneUnixSocketKeepsItsPath() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisClusterConnectOptions().addConnectionString("unix:///var/run/redis.sock"));

    assertThat(target.getAddress()).isEqualTo("/var/run/redis.sock");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void clusterSortsIpv6EndpointsWithoutDoubleBracketing() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisClusterConnectOptions()
                .addConnectionString("redis://[2001:db8::2]:7001")
                .addConnectionString("redis://[2001:db8::1]:7000"));

    assertThat(target.getAddress()).isEqualTo("[2001:db8::1]:7000,[2001:db8::2]:7001");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void sentinelsAreScopedByTheirMaster() {
    RedisServerTarget first =
        VertxRedisServerTargets.of(
            new RedisSentinelConnectOptions()
                .setMasterName("themaster")
                .addConnectionString("redis://sentinel2:26380")
                .addConnectionString("redis://sentinel1:26379"));
    RedisServerTarget second =
        VertxRedisServerTargets.of(
            new RedisSentinelConnectOptions()
                .setMasterName("themaster")
                .addConnectionString("redis://sentinel1:26379")
                .addConnectionString("redis://sentinel2:26380"));

    assertThat(first.getAddress()).isEqualTo("sentinel1:26379,sentinel2:26380/themaster");
    assertThat(second.getAddress()).isEqualTo(first.getAddress());
    assertThat(first.getPort()).isNull();
  }

  @Test
  void sentinelPreservesDuplicateDiscoveryEndpoints() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisSentinelConnectOptions()
                .setMasterName("themaster")
                .addConnectionString("redis://sentinel:26379")
                .addConnectionString("redis://sentinel:26379"));

    assertThat(target.getAddress()).isEqualTo("sentinel:26379,sentinel:26379/themaster");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void sentinelOmitsUnsafeMasterSuffix() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisSentinelConnectOptions()
                .setMasterName("tenant/master")
                .addConnectionString("redis://sentinel:26379"));

    assertThat(target.getAddress()).isEqualTo("sentinel:26379");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void invalidClusterEndpointMakesTheTargetUnrepresentable() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisClusterConnectOptions()
                .addConnectionString("redis://working-cluster-seed:7000")
                .addConnectionString("redis://"));

    assertThat(target).isNull();
  }

  @Test
  void sentinelUsesTheEffectiveDefaultPort() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisSentinelConnectOptions()
                .setMasterName("themaster")
                .addConnectionString("redis://sentinel"));

    assertThat(target.getAddress()).isEqualTo("sentinel:6379/themaster");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void sentinelWithoutMasterKeepsTheSentinelEndpoints() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisSentinelConnectOptions()
                .setMasterName("  ")
                .addConnectionString("redis://sentinel1:26379")
                .addConnectionString("redis://sentinel2:26380"));

    assertThat(target.getAddress()).isEqualTo("sentinel1:26379,sentinel2:26380");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void laterOptionChangesDoNotChangeTheTarget() {
    RedisConnectOptions options =
        new RedisClusterConnectOptions()
            .addConnectionString("redis://node1:7000")
            .addConnectionString("redis://node2:7001");
    RedisServerTarget target = VertxRedisServerTargets.of(options);

    options.setConnectionString("redis://other:6379");

    assertThat(target.getAddress()).isEqualTo("node1:7000,node2:7001");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void capturedConnectOptionsAreIndependentAndImmutable() {
    RedisStandaloneConnectOptions first =
        new RedisStandaloneConnectOptions().setConnectionString("redis://first:6379");
    RedisStandaloneConnectOptions second =
        new RedisStandaloneConnectOptions().setConnectionString("redis://second:6380");
    VertxRedisServerTargets.capture(first);
    VertxRedisServerTargets.capture(second);

    first.setConnectionString("redis://changed:1234");

    assertThat(VertxRedisServerTargets.get(first).getAddress()).isEqualTo("first");
    assertThat(VertxRedisServerTargets.get(first).getPort()).isNull();
    assertThat(VertxRedisServerTargets.get(second).getAddress()).isEqualTo("second");
    assertThat(VertxRedisServerTargets.get(second).getPort()).isEqualTo(6380);
  }

  @Test
  void configuredFactoryTargetIsAttachedToSupplier() {
    Supplier<Object> supplier = Object::new;
    VertxRedisServerTargets.pushFactoryTarget(
        new RedisOptions().setConnectionString("redis://configured:6380"));
    try {
      VertxRedisServerTargets.capture(supplier);
    } finally {
      VertxRedisServerTargets.popFactoryTarget();
    }

    assertThat(VertxRedisServerTargets.get(supplier).getAddress()).isEqualTo("configured");
    assertThat(VertxRedisServerTargets.get(supplier).getPort()).isEqualTo(6380);
  }

  @Test
  void dynamicSupplierHasNoStableTarget() {
    Supplier<Object> supplier = Object::new;
    VertxRedisServerTargets.capture(supplier);

    assertThat(VertxRedisServerTargets.get(supplier)).isNull();
  }

  @Test
  void noOptions() {
    assertThat(VertxRedisServerTargets.of((RedisConnectOptions) null)).isNull();
    assertThat(VertxRedisServerTargets.of((RedisOptions) null)).isNull();
  }

  static class StaticReplicationRedisOptions extends RedisOptions {
    private StaticReplicationRedisOptions() {
      setType(RedisClientType.REPLICATION);
    }

    public String getTopology() {
      return "STATIC";
    }

    @Override
    public StaticReplicationRedisOptions addConnectionString(String connectionString) {
      super.addConnectionString(connectionString);
      return this;
    }
  }

  static class StaticReplicationConnectOptions extends RedisConnectOptions {
    public String getTopology() {
      return "STATIC";
    }

    @Override
    public StaticReplicationConnectOptions addConnectionString(String connectionString) {
      super.addConnectionString(connectionString);
      return this;
    }
  }
}
