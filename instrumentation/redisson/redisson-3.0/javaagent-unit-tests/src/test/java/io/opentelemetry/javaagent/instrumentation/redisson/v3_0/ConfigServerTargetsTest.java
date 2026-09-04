/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.v3_0;

import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.redisson.config.Config;
import org.redisson.config.ConfigServerTargetsBefore317;
import org.redisson.config.MasterSlaveServersConfig;

class ConfigServerTargetsTest {

  @Test
  void sentinelsAreScopedByTheirMaster() {
    Config config = new Config();
    config
        .useSentinelServers()
        .setMasterName("mymaster")
        .addSentinelAddress(redisAddress("sentinel2:26380"), redisAddress("sentinel1:26379"));

    RedisServerTarget target = ConfigServerTargetsBefore317.of(config);

    assertThat(target.getAddress()).isEqualTo("sentinel1:26379,sentinel2:26380/mymaster");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void clusterPermutationsRenderIdenticallyAfterNormalization() {
    Config first = new Config();
    first
        .useClusterServers()
        .addNodeAddress(redisAddress("node2:7001"))
        .addNodeAddress(secureRedisAddress("node1:7000"));
    Config second = new Config();
    second
        .useClusterServers()
        .addNodeAddress(secureRedisAddress("node1:7000"))
        .addNodeAddress(redisAddress("node2:7001"));

    RedisServerTarget firstTarget = ConfigServerTargetsBefore317.of(first);
    RedisServerTarget secondTarget = ConfigServerTargetsBefore317.of(second);

    assertThat(firstTarget.getAddress()).isEqualTo("node1:7000,node2:7001");
    assertThat(secondTarget.getAddress()).isEqualTo(firstTarget.getAddress());
    assertThat(firstTarget.getPort()).isNull();
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
        .addSlaveAddress(redisAddress("replica2:6381"), secureRedisAddress("replica1:6380"));

    assertThat(ConfigServerTargetsBefore317.of(config).getAddress())
        .isEqualTo("master:6379,replica1:6380,replica2:6381");
  }

  @ParameterizedTest
  @MethodSource("invalidReplicaAddresses")
  void masterAndReplicasFailClosedWhenAnyReplicaIsInvalid(Object invalidReplica)
      throws ReflectiveOperationException {
    Config config = new Config();
    MasterSlaveServersConfig serverConfig =
        config
            .useMasterSlaveServers()
            .setMasterAddress(redisAddress("master:6379"))
            .addSlaveAddress(redisAddress("replica:6380"));
    Object validReplica = serverConfig.getSlaveAddresses().iterator().next();
    Set<Object> replicas = new LinkedHashSet<>(asList(validReplica, invalidReplica));
    serverConfig
        .getClass()
        .getMethod("setSlaveAddresses", Set.class)
        .invoke(serverConfig, replicas);

    assertThat(ConfigServerTargetsBefore317.of(config)).isNull();
  }

  private static Stream<Arguments> invalidReplicaAddresses() {
    return Stream.of(
        argumentSet("null", (Object) null),
        argumentSet("malformed", URI.create("redis://replica:99999")),
        argumentSet(
            "unsupported conversion",
            new Object() {
              @Override
              public String toString() {
                throw new IllegalStateException("conversion failed");
              }
            }));
  }

  @Test
  void singleServerOmitsTheDefaultPort() {
    Config config = new Config();
    config.useSingleServer().setAddress(redisAddress("localhost:6379"));

    RedisServerTarget target = ConfigServerTargetsBefore317.of(config);

    assertThat(target.getAddress()).isEqualTo("localhost");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void singleServerUsesSanitizedConfiguredAddress() {
    Config config = new Config();
    config
        .useSingleServer()
        .setAddress(redisAddress("user:password@secure.example:6380/2?timeout=5s"));

    RedisServerTarget target = ConfigServerTargetsBefore317.of(config);

    assertThat(target.getAddress()).isEqualTo("secure.example");
    assertThat(target.getPort()).isEqualTo(6380);
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

  private static String secureRedisAddress(String address) {
    return (testLatestDeps() ? "rediss://" : "") + address;
  }

  private static Method findMethod(String methodName) {
    try {
      return Config.class.getMethod(methodName);
    } catch (NoSuchMethodException ignored) {
      return null;
    }
  }
}
