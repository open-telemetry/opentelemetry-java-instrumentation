/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.v3_17;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import org.junit.jupiter.api.Test;
import org.redisson.config.Config;
import org.redisson.config.ConfigServerTargetsSince317;
import org.redisson.connection.ServiceManager;

class ConfigServerTargetsTest {

  @Test
  void sentinelsAreScopedByTheirMaster() {
    Config config = new Config();
    config
        .useSentinelServers()
        .setMasterName("mymaster")
        .addSentinelAddress("redis://sentinel1:26379", "redis://sentinel2:26380");

    RedisServerTarget target = ConfigServerTargetsSince317.of(config);

    assertThat(target.getAddress()).isEqualTo("sentinel1:26379,sentinel2:26380/mymaster");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void clusterKeepsEveryConfiguredNode() {
    Config config = new Config();
    config
        .useClusterServers()
        .addNodeAddress("redis://node2:7001")
        .addNodeAddress("redis://node1:7000");

    RedisServerTarget target = ConfigServerTargetsSince317.of(config);

    assertThat(target.getAddress()).isEqualTo("node1:7000,node2:7001");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void clusterWithOneNodeKeepsItsPort() {
    Config config = new Config();
    config.useClusterServers().addNodeAddress("redis://node1:7000");

    RedisServerTarget target = ConfigServerTargetsSince317.of(config);

    assertThat(target.getAddress()).isEqualTo("node1");
    assertThat(target.getPort()).isEqualTo(7000);
  }

  @Test
  void replicatedKeepsEveryConfiguredNode() {
    Config config = new Config();
    config
        .useReplicatedServers()
        .addNodeAddress("redis://node2:6380")
        .addNodeAddress("redis://node1:6379");

    RedisServerTarget target = ConfigServerTargetsSince317.of(config);

    assertThat(target.getAddress()).isEqualTo("node1:6379,node2:6380");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void masterAndReplicasKeepEveryConfiguredEndpoint() {
    Config config = new Config();
    config
        .useMasterSlaveServers()
        .setMasterAddress("redis://master:6379")
        .addSlaveAddress("redis://replica1:6380", "redis://replica2:6381");

    assertThat(ConfigServerTargetsSince317.of(config).getAddress())
        .isEqualTo("master:6379,replica1:6380,replica2:6381");
  }

  @Test
  void singleServerNeedsNoTargetOfItsOwn() {
    Config config = new Config();
    config.useSingleServer().setAddress("redis://localhost:6379");

    assertThat(ConfigServerTargetsSince317.of(config)).isNull();
  }

  @Test
  void noConfig() {
    assertThat(ConfigServerTargetsSince317.of(null)).isNull();
  }

  @Test
  void serviceManagerCarriesTheConfiguration() {
    Config config = new Config();
    config.useSentinelServers().setMasterName("mymaster").addSentinelAddress("redis://s1:26379");

    RedisServerTarget target =
        ConfigServerTargetsSince317.ofServiceManager(new ServiceManager(config));

    assertThat(target.getAddress()).isEqualTo("s1:26379/mymaster");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void noServiceManager() {
    assertThat(ConfigServerTargetsSince317.ofServiceManager(null)).isNull();
    assertThat(ConfigServerTargetsSince317.ofServiceManager("not a service manager")).isNull();
  }
}
