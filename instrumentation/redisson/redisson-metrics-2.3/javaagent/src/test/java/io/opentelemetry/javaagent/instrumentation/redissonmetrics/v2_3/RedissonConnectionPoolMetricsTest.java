/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redissonmetrics.v2_3;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.javaagent.instrumentation.redissonmetrics.v2_3.RedissonConnectionPoolMetrics.INSTRUMENTATION_NAME;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.db.DbConnectionPoolMetricsAssertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.redisson.Redisson;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.testcontainers.containers.GenericContainer;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedissonConnectionPoolMetricsTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private final GenericContainer<?> redisServer =
      new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379);

  @BeforeAll
  void setupAll() {
    redisServer.start();
  }

  @AfterAll
  void cleanupAll() {
    redisServer.stop();
  }

  @Test
  void shouldReportMetrics() throws ReflectiveOperationException {
    testing.clearData();
    RedissonClient redisson = createRedissonClient();
    try {
      RBucket<String> bucket = redisson.getBucket("poolMetrics");
      bucket.set("value");

      DbConnectionPoolMetricsAssertions assertions =
          DbConnectionPoolMetricsAssertions.create(
                  testing, INSTRUMENTATION_NAME, expectedPoolName())
              .disableConnectionTimeouts()
              .disableCreateTime()
              .disableWaitTime()
              .disableUseTime();

      if (!testLatestDeps()) {
        assertions.disablePendingRequests();
      }

      assertConnectionUsageValues();
      assertions.assertConnectionPoolEmitsMetrics();

      redisson.shutdown();
      redisson = null;

      testing.clearData();

      await()
          .untilAsserted(
              () ->
                  assertThat(testing.metrics())
                      .filteredOn(
                          metricData ->
                              metricData
                                  .getInstrumentationScopeInfo()
                                  .getName()
                                  .equals(INSTRUMENTATION_NAME))
                      .isEmpty());
    } finally {
      if (redisson != null) {
        redisson.shutdown();
      }
    }
  }

  private RedissonClient createRedissonClient() throws ReflectiveOperationException {
    String address = redisAddress();
    if (testLatestDeps()) {
      address = "redis://" + address;
    }

    Config config = new Config();
    SingleServerConfig singleServerConfig = config.useSingleServer();
    singleServerConfig.setAddress(address);
    singleServerConfig.setTimeout(30_000);
    singleServerConfig.setConnectionPoolSize(10);
    singleServerConfig.setConnectionMinimumIdleSize(5);
    try {
      singleServerConfig
          .getClass()
          .getMethod("setPingConnectionInterval", int.class)
          .invoke(singleServerConfig, 0);
    } catch (NoSuchMethodException ignored) {
      // ignored
    }
    return Redisson.create(config);
  }

  private void assertConnectionUsageValues() {
    String poolNameKey =
        emitStableDatabaseSemconv() ? "db.client.connection.pool.name" : "pool.name";
    String stateKey = emitStableDatabaseSemconv() ? "db.client.connection.state" : "state";

    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        emitStableDatabaseSemconv() ? "db.client.connection.count" : "db.client.connections.usage",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasLongSumSatisfying(
                            sum ->
                                sum.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasValue(5)
                                            .hasAttributesSatisfying(
                                                equalTo(stringKey(poolNameKey), expectedPoolName()),
                                                equalTo(stringKey(stateKey), "idle")),
                                    point ->
                                        point
                                            .hasValue(0)
                                            .hasAttributesSatisfying(
                                                equalTo(stringKey(poolNameKey), expectedPoolName()),
                                                equalTo(stringKey(stateKey), "used"))))));
  }

  private String expectedPoolName() {
    return "master-" + redisAddress();
  }

  private String redisAddress() {
    return "localhost:" + redisServer.getMappedPort(6379);
  }
}
