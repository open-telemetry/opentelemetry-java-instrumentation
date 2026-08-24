/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_0;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.vertx.redis.client.RedisClientType;
import io.vertx.redis.client.RedisOptions;
import org.junit.jupiter.api.Test;

class VertxRedisServerTargetsTest {

  @Test
  void standalone() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(new RedisOptions().setConnectionString("redis://host:6379"));

    assertThat(target.getAddress()).isEqualTo("host");
    assertThat(target.getPort()).isEqualTo(6379);
  }

  @Test
  void standaloneDropsCredentialsDatabaseAndQuery() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisOptions()
                .setConnectionString("redis://user:secret@host:6379/2?client_name=app#fragment"));

    assertThat(target.getAddress()).isEqualTo("host");
    assertThat(target.getPort()).isEqualTo(6379);
  }

  @Test
  void standaloneIgnoresTheEndpointsItNeverConnectsTo() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisOptions()
                .addConnectionString("redis://host1:6379")
                .addConnectionString("redis://host2:6380"));

    assertThat(target.getAddress()).isEqualTo("host1");
    assertThat(target.getPort()).isEqualTo(6379);
  }

  @Test
  void standaloneKeepsItsDefaultMasterNameOut() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisOptions().setConnectionString("redis://host:6379").setMasterName("mymaster"));

    assertThat(target.getAddress()).isEqualTo("host");
    assertThat(target.getPort()).isEqualTo(6379);
  }

  @Test
  void clusterKeepsEveryConfiguredEndpoint() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisOptions()
                .setType(RedisClientType.CLUSTER)
                .addConnectionString("redis://node1:7000")
                .addConnectionString("redis://node2:7001"));

    assertThat(target.getAddress()).isEqualTo("redis://node1:7000,redis://node2:7001");
    assertThat(target.getPort()).isNull();
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
  void clusterDropsCredentialsAndDatabase() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisOptions()
                .setType(RedisClientType.CLUSTER)
                .addConnectionString("redis://user:secret@node1:7000/2")
                .addConnectionString("redis://node2:7001"));

    assertThat(target.getAddress()).isEqualTo("redis://node1:7000,redis://node2:7001");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void sentinelIsNamedByItsMaster() {
    RedisServerTarget target =
        VertxRedisServerTargets.of(
            new RedisOptions()
                .setType(RedisClientType.SENTINEL)
                .setMasterName("themaster")
                .addConnectionString("redis://sentinel1:26379")
                .addConnectionString("redis://sentinel2:26380"));

    assertThat(target.getAddress()).isEqualTo("themaster");
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

    assertThat(target.getAddress()).isEqualTo("redis://sentinel1:26379,redis://sentinel2:26380");
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

    assertThat(target.getAddress()).isEqualTo("redis://node1:7000,redis://node2:7001");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void noOptions() {
    assertThat(VertxRedisServerTargets.of(null)).isNull();
  }
}
