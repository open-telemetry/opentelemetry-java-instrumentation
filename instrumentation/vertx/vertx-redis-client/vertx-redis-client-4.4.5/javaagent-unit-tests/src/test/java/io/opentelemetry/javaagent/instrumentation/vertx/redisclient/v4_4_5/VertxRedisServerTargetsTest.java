/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_4_5;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.vertx.core.Vertx;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.redis.client.PoolOptions;
import io.vertx.redis.client.RedisClusterConnectOptions;
import io.vertx.redis.client.RedisConnectOptions;
import io.vertx.redis.client.RedisSentinelConnectOptions;
import io.vertx.redis.client.RedisStandaloneConnectOptions;
import io.vertx.redis.client.impl.RedisStandaloneConnection;
import io.vertx.redis.client.impl.RedisURI;
import org.junit.jupiter.api.Test;

class VertxRedisServerTargetsTest {

  @Test
  void standalone() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisStandaloneConnectOptions().setConnectionString("redis://host:6379"));

    assertThat(target.getAddress()).isEqualTo("host");
    assertThat(target.getPort()).isEqualTo(6379);
  }

  @Test
  void standaloneUsesTheEffectiveDefaultPort() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisStandaloneConnectOptions().setConnectionString("redis://host"));

    assertThat(target.getAddress()).isEqualTo("host");
    assertThat(target.getPort()).isEqualTo(6379);
  }

  @Test
  void standaloneDropsCredentialsDatabaseAndQuery() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisStandaloneConnectOptions()
                .setConnectionString("redis://user:secret@host:6379/2?client_name=app#fragment"));

    assertThat(target.getAddress()).isEqualTo("host");
    assertThat(target.getPort()).isEqualTo(6379);
  }

  @Test
  void standaloneIgnoresTheEndpointsItNeverConnectsTo() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisStandaloneConnectOptions()
                .addConnectionString("redis://host1:6379")
                .addConnectionString("redis://host2:6380"));

    assertThat(target.getAddress()).isEqualTo("host1");
    assertThat(target.getPort()).isEqualTo(6379);
  }

  @Test
  void clusterKeepsEveryConfiguredEndpoint() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisClusterConnectOptions()
                .addConnectionString("redis://node1:7000")
                .addConnectionString("redis://node2:7001"));

    assertThat(target.getAddress()).isEqualTo("node1:7000,node2:7001");
    assertThat(target.getPort()).isNull();
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
  void sentinelsAreScopedByTheirMaster() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisSentinelConnectOptions()
                .setMasterName("themaster")
                .addConnectionString("redis://sentinel1:26379")
                .addConnectionString("redis://sentinel2:26380"));

    assertThat(target.getAddress()).isEqualTo("sentinel1:26379,sentinel2:26380/themaster");
    assertThat(target.getPort()).isNull();
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
  void connectionTargetsAreIndependent() {
    Vertx vertx = Vertx.vertx();
    try {
      VertxInternal vertxInternal = (VertxInternal) vertx;
      ContextInternal context = vertxInternal.getOrCreateContext();
      RedisURI redisUri = new RedisURI("redis://host:6379");
      RedisServerTarget firstTarget = RedisServerTarget.ofEndpoint("redis://first:6379");
      RedisServerTarget secondTarget = RedisServerTarget.ofEndpoint("redis://second:6380");
      RedisStandaloneConnection firstConnection = connection(vertxInternal, context, redisUri);
      RedisStandaloneConnection secondConnection = connection(vertxInternal, context, redisUri);

      VertxRedisServerTargets.set(redisUri, firstTarget);
      VertxRedisServerTargets.setConnectionTarget(firstConnection, redisUri);
      VertxRedisServerTargets.set(redisUri, secondTarget);
      VertxRedisServerTargets.setConnectionTarget(secondConnection, redisUri);

      assertThat(VertxRedisServerTargets.getConnectionTarget(firstConnection))
          .isSameAs(firstTarget);
      assertThat(VertxRedisServerTargets.getConnectionTarget(secondConnection))
          .isSameAs(secondTarget);
    } finally {
      vertx.close();
    }
  }

  @Test
  void noOptions() {
    assertThat(VertxRedisServerTargets.of(null)).isNull();
  }

  private static RedisStandaloneConnection connection(
      VertxInternal vertx, ContextInternal context, RedisURI redisUri) {
    return new RedisStandaloneConnection(
        vertx, context, null, null, new PoolOptions(), 1, redisUri, null, null);
  }
}
