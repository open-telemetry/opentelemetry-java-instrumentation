/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.v3_17;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
        .addSentinelAddress("redis://sentinel2:26380", "redis://sentinel1:26379");

    RedisServerTarget target = ConfigServerTargetsSince317.of(config);

    assertThat(target.getAddress()).isEqualTo("sentinel1:26379,sentinel2:26380/mymaster");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void clusterPermutationsRenderIdenticallyAfterNormalization() {
    Config first = new Config();
    first
        .useClusterServers()
        .addNodeAddress("redis://node2:7001")
        .addNodeAddress("rediss://node1:7000");
    Config second = new Config();
    second
        .useClusterServers()
        .addNodeAddress("rediss://node1:7000")
        .addNodeAddress("redis://node2:7001");

    RedisServerTarget firstTarget = ConfigServerTargetsSince317.of(first);
    RedisServerTarget secondTarget = ConfigServerTargetsSince317.of(second);

    assertThat(firstTarget.getAddress()).isEqualTo("node1:7000,node2:7001");
    assertThat(secondTarget.getAddress()).isEqualTo(firstTarget.getAddress());
    assertThat(firstTarget.getPort()).isNull();
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
  void replicatedWithOneNodeKeepsItsPort() {
    Config config = new Config();
    config.useReplicatedServers().addNodeAddress("redis://node1:6380");

    RedisServerTarget target = ConfigServerTargetsSince317.of(config);

    assertThat(target.getAddress()).isEqualTo("node1");
    assertThat(target.getPort()).isEqualTo(6380);
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
        .addSlaveAddress("redis://replica2:6381", "rediss://replica1:6380");

    assertThat(ConfigServerTargetsSince317.of(config).getAddress())
        .isEqualTo("master:6379,replica1:6380,replica2:6381");
  }

  @Test
  void masterWithoutReplicasOmitsTheDefaultPort() {
    Config config = new Config();
    config.useMasterSlaveServers().setMasterAddress("redis://master:6379");

    RedisServerTarget target = ConfigServerTargetsSince317.of(config);

    assertThat(target.getAddress()).isEqualTo("master");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void singleServerOmitsTheDefaultPort() {
    Config config = new Config();
    config.useSingleServer().setAddress("redis://localhost:6379");

    RedisServerTarget target = ConfigServerTargetsSince317.of(config);

    assertThat(target.getAddress()).isEqualTo("localhost");
    assertThat(target.getPort()).isNull();
  }

  @ParameterizedTest
  @CsvSource({
    "rediss://user:password@secure.example:6380/2?timeout=5s, secure.example, 6380",
    "redis://[2001:db8::1]:6381, 2001:db8::1, 6381"
  })
  void singleServerUsesSanitizedConfiguredAddress(
      String configuredAddress, String expectedAddress, int expectedPort) {
    Config config = new Config();
    config.useSingleServer().setAddress(configuredAddress);

    RedisServerTarget target = ConfigServerTargetsSince317.of(config);

    assertThat(target.getAddress()).isEqualTo(expectedAddress);
    assertThat(target.getPort()).isEqualTo(expectedPort);
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
