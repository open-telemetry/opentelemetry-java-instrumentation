/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.v3_17;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import org.junit.jupiter.api.Test;
import org.redisson.config.Config;
import org.redisson.config.ConfigServerTargets;
import org.redisson.connection.ServiceManager;

class ConfigServerTargetsTest {

  @Test
  void sentinelIsNamedByItsMaster() {
    Config config = new Config();
    config
        .useSentinelServers()
        .setMasterName("mymaster")
        .addSentinelAddress("redis://sentinel1:26379", "redis://sentinel2:26380");

    RedisServerTarget target = ConfigServerTargets.of(config);

    assertThat(target.getAddress()).isEqualTo("mymaster");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void clusterKeepsEveryConfiguredNode() {
    Config config = new Config();
    config
        .useClusterServers()
        .addNodeAddress("redis://node1:7000")
        .addNodeAddress("redis://node2:7001");

    RedisServerTarget target = ConfigServerTargets.of(config);

    assertThat(target.getAddress()).isEqualTo("redis://node1:7000,redis://node2:7001");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void clusterWithOneNodeKeepsItsPort() {
    Config config = new Config();
    config.useClusterServers().addNodeAddress("redis://node1:7000");

    RedisServerTarget target = ConfigServerTargets.of(config);

    assertThat(target.getAddress()).isEqualTo("node1");
    assertThat(target.getPort()).isEqualTo(7000);
  }

  @Test
  void replicatedKeepsEveryConfiguredNode() {
    Config config = new Config();
    config
        .useReplicatedServers()
        .addNodeAddress("redis://node1:6379")
        .addNodeAddress("redis://node2:6380");

    RedisServerTarget target = ConfigServerTargets.of(config);

    assertThat(target.getAddress()).isEqualTo("redis://node1:6379,redis://node2:6380");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void singleServerNeedsNoTargetOfItsOwn() {
    Config config = new Config();
    config.useSingleServer().setAddress("redis://localhost:6379");

    assertThat(ConfigServerTargets.of(config)).isNull();
  }

  @Test
  void noConfig() {
    assertThat(ConfigServerTargets.of(null)).isNull();
  }

  @Test
  void serviceManagerCarriesTheConfiguration() {
    Config config = new Config();
    config.useSentinelServers().setMasterName("mymaster").addSentinelAddress("redis://s1:26379");

    RedisServerTarget target = ConfigServerTargets.ofServiceManager(new ServiceManager(config));

    assertThat(target.getAddress()).isEqualTo("mymaster");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void noServiceManager() {
    assertThat(ConfigServerTargets.ofServiceManager(null)).isNull();
    assertThat(ConfigServerTargets.ofServiceManager("not a service manager")).isNull();
  }
}
