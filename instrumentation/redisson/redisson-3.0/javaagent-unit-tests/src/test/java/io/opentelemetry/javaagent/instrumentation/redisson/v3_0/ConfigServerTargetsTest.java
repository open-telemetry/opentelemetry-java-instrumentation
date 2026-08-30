/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.v3_0;

import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.redisson.config.Config;
import org.redisson.config.ConfigServerTargetsBefore317;

class ConfigServerTargetsTest {

  @Test
  void sentinelsAreScopedByTheirMaster() {
    Config config = new Config();
    config
        .useSentinelServers()
        .setMasterName("mymaster")
        .addSentinelAddress(redisAddress("sentinel1:26379"), redisAddress("sentinel2:26380"));

    RedisServerTarget target = ConfigServerTargetsBefore317.of(config);

    assertThat(target.getAddress()).isEqualTo("sentinel1:26379,sentinel2:26380/mymaster");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void clusterKeepsEveryConfiguredNodeInStableOrder() {
    Config config = new Config();
    config
        .useClusterServers()
        .addNodeAddress(redisAddress("node2:7001"))
        .addNodeAddress(redisAddress("node1:7000"));

    RedisServerTarget target = ConfigServerTargetsBefore317.of(config);

    assertThat(target.getAddress()).isEqualTo("node1:7000,node2:7001");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void clusterWithOneNodeKeepsItsPort() {
    Config config = new Config();
    config.useClusterServers().addNodeAddress(redisAddress("node1:7000"));

    RedisServerTarget target = ConfigServerTargetsBefore317.of(config);

    assertThat(target.getAddress()).isEqualTo("node1");
    assertThat(target.getPort()).isEqualTo(7000);
  }

  @Test
  void elasticacheKeepsEveryConfiguredNode() throws ReflectiveOperationException {
    Method useElasticacheServers = findMethod("useElasticacheServers");
    assumeTrue(useElasticacheServers != null);
    Config config = configWithServers(useElasticacheServers);

    RedisServerTarget target = ConfigServerTargetsBefore317.of(config);

    assertThat(target.getAddress()).isEqualTo("node1:6379,node2:6380");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void replicatedKeepsEveryConfiguredNodeWhenSupported() throws ReflectiveOperationException {
    Method useReplicatedServers = findMethod("useReplicatedServers");
    assumeTrue(useReplicatedServers != null);
    Config config = configWithServers(useReplicatedServers);

    RedisServerTarget target = ConfigServerTargetsBefore317.of(config);

    assertThat(target.getAddress()).isEqualTo("node1:6379,node2:6380");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void masterAndReplicasKeepEveryConfiguredEndpoint() {
    Config config = new Config();
    config
        .useMasterSlaveServers()
        .setMasterAddress(redisAddress("master:6379"))
        .addSlaveAddress(redisAddress("replica2:6381"), redisAddress("replica1:6380"));

    assertThat(ConfigServerTargetsBefore317.of(config).getAddress())
        .isEqualTo("master:6379,replica1:6380,replica2:6381");
  }

  @ParameterizedTest
  @CsvSource({
    "localhost:6379, localhost, 6379",
    "user:password@secure.example:6380/2?timeout=5s, secure.example, 6380"
  })
  void singleServerUsesSanitizedConfiguredAddress(
      String configuredAddress, String expectedAddress, int expectedPort) {
    Config config = new Config();
    config.useSingleServer().setAddress(redisAddress(configuredAddress));

    RedisServerTarget target = ConfigServerTargetsBefore317.of(config);

    assertThat(target.getAddress()).isEqualTo(expectedAddress);
    assertThat(target.getPort()).isEqualTo(expectedPort);
  }

  @Test
  void noConfig() {
    assertThat(ConfigServerTargetsBefore317.of(null)).isNull();
  }

  private static Config configWithServers(Method method) throws ReflectiveOperationException {
    Config config = new Config();
    Object serverConfig = method.invoke(config);
    serverConfig
        .getClass()
        .getMethod("addNodeAddress", String[].class)
        .invoke(
            serverConfig,
            (Object) new String[] {redisAddress("node2:6380"), redisAddress("node1:6379")});
    return config;
  }

  private static String redisAddress(String address) {
    return (testLatestDeps() ? "redis://" : "") + address;
  }

  private static Method findMethod(String methodName) {
    try {
      return Config.class.getMethod(methodName);
    } catch (NoSuchMethodException ignored) {
      return null;
    }
  }
}
