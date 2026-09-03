/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_0;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.vertx.redis.client.RedisClientType;
import io.vertx.redis.client.RedisOptions;
import io.vertx.redis.client.impl.RedisURI;
import org.junit.jupiter.api.Test;

class VertxRedisServerTargetsTest {

  @Test
  void standalone() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(new RedisOptions().setConnectionString("redis://host:6379"));

    assertThat(target.getAddress()).isEqualTo("host");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void standaloneUsesTheEffectiveDefaultPort() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(new RedisOptions().setConnectionString("redis://host"));

    assertThat(target.getAddress()).isEqualTo("host");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void standaloneDropsCredentialsDatabaseAndQuery() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisOptions()
                .setConnectionString("redis://user:secret@host:6379/2?client_name=app#fragment"));

    assertThat(target.getAddress()).isEqualTo("host");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void standaloneIgnoresTheEndpointsItNeverConnectsTo() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisOptions()
                .addConnectionString("redis://host1:6379")
                .addConnectionString("redis://host2:6380"));

    assertThat(target.getAddress()).isEqualTo("host1");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void standaloneKeepsItsDefaultMasterNameOut() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisOptions().setConnectionString("redis://host:6379").setMasterName("mymaster"));

    assertThat(target.getAddress()).isEqualTo("host");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void standaloneKeepsNonDefaultPortSeparate() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(new RedisOptions().setConnectionString("redis://host:6380"));

    assertThat(target.getAddress()).isEqualTo("host");
    assertThat(target.getPort()).isEqualTo(6380);
  }

  @Test
  void clusterEndpointPermutationsRenderIdentically() {
    RedisServerTarget first =
        VertxRedisServerTargets.of(
            new RedisOptions()
                .setType(RedisClientType.CLUSTER)
                .addConnectionString("redis://node2:7001")
                .addConnectionString("redis://node1:7000"));
    RedisServerTarget second =
        VertxRedisServerTargets.of(
            new RedisOptions()
                .setType(RedisClientType.CLUSTER)
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
            new RedisOptions()
                .setType(RedisClientType.CLUSTER)
                .addConnectionString("redis://node1:7000"));

    assertThat(target.getAddress()).isEqualTo("node1");
    assertThat(target.getPort()).isEqualTo(7000);
  }

  @Test
  void clusterOmitsSharedDefaultPort() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisOptions()
                .setType(RedisClientType.CLUSTER)
                .addConnectionString("redis://node2")
                .addConnectionString("redis://node1:6379"));

    assertThat(target.getAddress()).isEqualTo("node1,node2");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void clusterExtractsSharedNonDefaultPort() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisOptions()
                .setType(RedisClientType.CLUSTER)
                .addConnectionString("redis://node2:7000")
                .addConnectionString("redis://node1:7000"));

    assertThat(target.getAddress()).isEqualTo("node1:7000,node2:7000");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void clusterDropsCredentialsAndDatabase() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisOptions()
                .setType(RedisClientType.CLUSTER)
                .addConnectionString("redis://user:secret@node1:7000/2")
                .addConnectionString("redis://node2:7001"));

    assertThat(target.getAddress()).isEqualTo("node1:7000,node2:7001");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void clusterUsesTheEffectiveDefaultPort() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisOptions()
                .setType(RedisClientType.CLUSTER)
                .addConnectionString("redis://node1")
                .addConnectionString("redis://node2:7001"));

    assertThat(target.getAddress()).isEqualTo("node1:6379,node2:7001");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void clusterKeepsDuplicateEffectiveEndpoints() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisOptions()
                .setType(RedisClientType.CLUSTER)
                .addConnectionString("redis://node1")
                .addConnectionString("redis://node1:6379"));

    assertThat(target.getAddress()).isEqualTo("node1,node1");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void clusterWithMultipleUnixSocketsIsUnrepresentable() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisOptions()
                .setType(RedisClientType.CLUSTER)
                .addConnectionString("unix:///var/run/redis2.sock")
                .addConnectionString("unix:///var/run/redis1.sock"));

    assertThat(target).isNull();
  }

  @Test
  void clusterWithOneUnixSocketKeepsItsPath() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisOptions()
                .setType(RedisClientType.CLUSTER)
                .addConnectionString("unix:///var/run/redis.sock"));

    assertThat(target.getAddress()).isEqualTo("/var/run/redis.sock");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void clusterSortsIpv6EndpointsWithoutDoubleBracketing() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisOptions()
                .setType(RedisClientType.CLUSTER)
                .addConnectionString("redis://[2001:db8::2]:7001")
                .addConnectionString("redis://[2001:db8::1]:7000"));

    assertThat(target.getAddress()).isEqualTo("[2001:db8::1]:7000,[2001:db8::2]:7001");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void sentinelsAreScopedByTheirMaster() {
    RedisServerTarget first =
        VertxRedisServerTargets.of(
            new RedisOptions()
                .setType(RedisClientType.SENTINEL)
                .setMasterName("themaster")
                .addConnectionString("redis://sentinel2:26380")
                .addConnectionString("redis://sentinel1:26379"));
    RedisServerTarget second =
        VertxRedisServerTargets.of(
            new RedisOptions()
                .setType(RedisClientType.SENTINEL)
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
            new RedisOptions()
                .setType(RedisClientType.SENTINEL)
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
            new RedisOptions()
                .setType(RedisClientType.SENTINEL)
                .setMasterName("tenant/master")
                .addConnectionString("redis://sentinel:26379"));

    assertThat(target.getAddress()).isEqualTo("sentinel:26379");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void invalidClusterEndpointMakesTheTargetUnrepresentable() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisOptions()
                .setType(RedisClientType.CLUSTER)
                .addConnectionString("redis://working-cluster-seed:7000")
                .addConnectionString("redis://"));

    assertThat(target).isNull();
  }

  @Test
  void sentinelUsesTheEffectiveDefaultPort() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisOptions()
                .setType(RedisClientType.SENTINEL)
                .setMasterName("themaster")
                .addConnectionString("redis://sentinel"));

    assertThat(target.getAddress()).isEqualTo("sentinel:6379/themaster");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void sentinelWithoutMasterKeepsTheSentinelEndpoints() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisOptions()
                .setType(RedisClientType.SENTINEL)
                .setMasterName("  ")
                .addConnectionString("redis://sentinel1:26379")
                .addConnectionString("redis://sentinel2:26380"));

    assertThat(target.getAddress()).isEqualTo("sentinel1:26379,sentinel2:26380");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void laterOptionChangesDoNotChangeTheTarget() {
    RedisOptions options =
        new RedisOptions()
            .setType(RedisClientType.CLUSTER)
            .addConnectionString("redis://node1:7000")
            .addConnectionString("redis://node2:7001");
    RedisServerTarget target = VertxRedisServerTargets.of(options);

    options
        .setType(RedisClientType.SENTINEL)
        .setMasterName("themaster")
        .setConnectionString("redis://other:6379");

    assertThat(target.getAddress()).isEqualTo("node1:7000,node2:7001");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void nullTargetClearsRedisUriAssociation() {
    RedisURI redisUri = new RedisURI("redis://host:6379");
    VirtualField<RedisURI, RedisServerTarget> targetField =
        VirtualField.find(RedisURI.class, RedisServerTarget.class);
    VertxRedisServerTargets.set(redisUri, RedisServerTarget.ofEndpoint("host:6379"));

    VertxRedisServerTargets.set(redisUri, null);

    assertThat(targetField.get(redisUri)).isNull();
  }

  @Test
  void noOptions() {
    assertThat(VertxRedisServerTargets.of(null)).isNull();
  }
}
