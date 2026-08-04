/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redissonmetrics.v3_26;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.javaagent.instrumentation.redissonmetrics.v3_26.RedissonConnectionPoolMetrics.INSTRUMENTATION_NAME;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.db.DbConnectionPoolMetricsAssertions;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.redisson.connection.ClientConnectionsEntry;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.ConnectionsHolder;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.misc.AsyncSemaphore;
import org.testcontainers.containers.GenericContainer;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedissonConnectionPoolMetricsTest {

  private static final int REDIS_PORT = 6379;
  private static final int REGULAR_MIN_IDLE = 5;
  private static final int REGULAR_MAX = 10;
  private static final int SUBSCRIPTION_MIN_IDLE = 2;
  private static final int SUBSCRIPTION_MAX = 4;

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private final GenericContainer<?> redisServer =
      new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(REDIS_PORT);

  private String endpoint;

  @BeforeAll
  void startRedis() throws UnknownHostException {
    redisServer.start();
    cleanup.deferAfterAll(redisServer::stop);

    String host = InetAddress.getByName(redisServer.getHost()).getHostAddress();
    if (host.indexOf(':') >= 0) {
      host = "[" + host + "]";
    }
    endpoint = host + ":" + redisServer.getMappedPort(REDIS_PORT);
  }

  @Test
  void reportsRegularAndSubscriptionPoolMetricsUntilClientShutdown()
      throws ReflectiveOperationException {
    testing.clearData();
    RedissonClient redisson = createRedissonClient();
    try {
      redisson.getBucket("pool-metrics").set("value");

      String regularPool = "master-regular-" + endpoint;
      String subscriptionPool = "master-subscription-" + endpoint;
      assertConnectionPoolMetrics(regularPool);
      assertConnectionPoolMetrics(subscriptionPool);
      assertMetricNotEmitted(maxIdleMetricName());

      ConnectionsHolder<?> holder = getMasterConnectionsHolder((Redisson) redisson);
      AsyncSemaphore semaphore = holder.getFreeConnectionsCounter();

      testing.clearData();
      semaphore.acquire().join();
      try {
        // Acquiring a permit alone does not open or remove a connection from the idle queue.
        assertUsageMetricUnchanged(regularPool, subscriptionPool);
      } finally {
        semaphore.release();
      }

      redisson.shutdown();
      redisson = null;
      testing.clearData();

      await()
          .untilAsserted(
              () ->
                  assertThat(testing.metrics())
                      .filteredOn(
                          metric ->
                              metric
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

  private RedissonClient createRedissonClient() {
    Config config = new Config();
    config.setCodec(StringCodec.INSTANCE);
    SingleServerConfig serverConfig =
        config
            .useSingleServer()
            .setAddress(
                "redis://" + redisServer.getHost() + ":" + redisServer.getMappedPort(REDIS_PORT))
            .setTimeout(30_000)
            .setConnectionMinimumIdleSize(REGULAR_MIN_IDLE)
            .setConnectionPoolSize(REGULAR_MAX)
            .setSubscriptionConnectionMinimumIdleSize(SUBSCRIPTION_MIN_IDLE)
            .setSubscriptionConnectionPoolSize(SUBSCRIPTION_MAX);
    serverConfig.setPingConnectionInterval(0);
    return Redisson.create(config);
  }

  private static void assertConnectionPoolMetrics(String poolName) {
    DbConnectionPoolMetricsAssertions.create(testing, INSTRUMENTATION_NAME, poolName)
        .disableMaxIdleConnections()
        .disableConnectionTimeouts()
        .disableCreateTime()
        .disableWaitTime()
        .disableUseTime()
        .assertConnectionPoolEmitsMetrics();
  }

  private static void assertUsageMetricUnchanged(String regularPool, String subscriptionPool) {
    AttributeKey<String> poolNameKey =
        stringKey(emitStableDatabaseSemconv() ? "db.client.connection.pool.name" : "pool.name");
    AttributeKey<String> stateKey =
        stringKey(emitStableDatabaseSemconv() ? "db.client.connection.state" : "state");

    await()
        .untilAsserted(
            () ->
                assertThat(testing.metrics())
                    .filteredOn(
                        metric ->
                            metric
                                    .getInstrumentationScopeInfo()
                                    .getName()
                                    .equals(INSTRUMENTATION_NAME)
                                && metric.getName().equals(usageMetricName()))
                    .hasSizeGreaterThanOrEqualTo(2)
                    .allSatisfy(
                        metric -> {
                          Collection<LongPointData> points = metric.getLongSumData().getPoints();
                          assertThat(points).hasSize(4);
                          assertPoint(points, poolNameKey, regularPool, stateKey, "idle", 0);
                          assertPoint(points, poolNameKey, regularPool, stateKey, "used", 0);
                          assertPoint(points, poolNameKey, subscriptionPool, stateKey, "idle", 0);
                          assertPoint(points, poolNameKey, subscriptionPool, stateKey, "used", 0);
                        }));
  }

  private static void assertPoint(
      Collection<LongPointData> points,
      AttributeKey<String> poolNameKey,
      String poolName,
      AttributeKey<String> stateKey,
      String state,
      long expectedValue) {
    assertThat(points)
        .anySatisfy(
            point -> {
              assertThat(point.getValue()).isEqualTo(expectedValue);
              assertThat(point.getAttributes())
                  .isEqualTo(Attributes.of(poolNameKey, poolName, stateKey, state));
            });
  }

  private static void assertMetricNotEmitted(String metricName) {
    assertThat(testing.metrics())
        .filteredOn(
            metric ->
                metric.getInstrumentationScopeInfo().getName().equals(INSTRUMENTATION_NAME)
                    && metric.getName().equals(metricName))
        .isEmpty();
  }

  private static ConnectionsHolder<?> getMasterConnectionsHolder(Redisson redisson)
      throws ReflectiveOperationException {
    Field connectionManagerField = Redisson.class.getDeclaredField("connectionManager");
    connectionManagerField.setAccessible(true);
    ConnectionManager connectionManager = (ConnectionManager) connectionManagerField.get(redisson);
    MasterSlaveEntry entry = connectionManager.getEntrySet().iterator().next();

    Field masterEntryField = MasterSlaveEntry.class.getDeclaredField("masterEntry");
    masterEntryField.setAccessible(true);
    ClientConnectionsEntry masterEntry = (ClientConnectionsEntry) masterEntryField.get(entry);
    return masterEntry.getConnectionsHolder();
  }

  private static String usageMetricName() {
    return emitStableDatabaseSemconv()
        ? "db.client.connection.count"
        : "db.client.connections.usage";
  }

  private static String maxIdleMetricName() {
    return emitStableDatabaseSemconv()
        ? "db.client.connection.idle.max"
        : "db.client.connections.idle.max";
  }
}
