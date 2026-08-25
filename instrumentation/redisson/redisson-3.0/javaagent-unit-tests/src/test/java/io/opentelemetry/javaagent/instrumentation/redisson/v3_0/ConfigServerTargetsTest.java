/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.v3_0;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import org.junit.jupiter.api.Test;
import org.redisson.config.Config;
import org.redisson.config.ConfigServerTargetsBefore317;

class ConfigServerTargetsTest {

  @Test
  void sentinelsAreScopedByTheirMaster() {
    Config config = new Config();
    config
        .useSentinelServers()
        .setMasterName("mymaster")
        .addSentinelAddress("redis://sentinel1:26379", "redis://sentinel2:26380");

    RedisServerTarget target = ConfigServerTargetsBefore317.of(config);

    assertThat(target.getAddress()).isEqualTo("sentinel1:26379/mymaster,sentinel2:26380/mymaster");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void clusterKeepsEveryConfiguredNodeInStableOrder() {
    Config config = new Config();
    config
        .useClusterServers()
        .addNodeAddress("redis://node2:7001")
        .addNodeAddress("redis://node1:7000");

    RedisServerTarget target = ConfigServerTargetsBefore317.of(config);

    assertThat(target.getAddress()).isEqualTo("node1:7000,node2:7001");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void clusterWithOneNodeKeepsItsPort() {
    Config config = new Config();
    config.useClusterServers().addNodeAddress("redis://node1:7000");

    RedisServerTarget target = ConfigServerTargetsBefore317.of(config);

    assertThat(target.getAddress()).isEqualTo("node1");
    assertThat(target.getPort()).isEqualTo(7000);
  }

  @Test
  void masterAndReplicasKeepEveryConfiguredEndpoint() {
    Config config = new Config();
    config
        .useMasterSlaveServers()
        .setMasterAddress("redis://master:6379")
        .addSlaveAddress("redis://replica2:6381", "redis://replica1:6380");

    assertThat(ConfigServerTargetsBefore317.of(config).getAddress())
        .isEqualTo("master:6379,replica1:6380,replica2:6381");
  }

  @Test
  void singleServerNeedsNoTargetOfItsOwn() {
    Config config = new Config();
    config.useSingleServer().setAddress("redis://localhost:6379");

    assertThat(ConfigServerTargetsBefore317.of(config)).isNull();
  }

  @Test
  void noConfig() {
    assertThat(ConfigServerTargetsBefore317.of(null)).isNull();
  }
}
