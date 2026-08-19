/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redissonmetrics.v3_26;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.javaagent.instrumentation.redissonmetrics.v3_26.RedissonConnectionPoolMetrics.INSTRUMENTATION_NAME;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
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
  void startRedis() {
    redisServer.start();
    cleanup.deferAfterAll(redisServer::stop);

    String host = redisServer.getHost();
    if (host.indexOf(':') >= 0) {
      host = "[" + host + "]";
    }
    endpoint = host + ":" + redisServer.getMappedPort(REDIS_PORT);
  }

  @Test
  void reportsRegularAndSubscriptionPoolMetricsUntilClientShutdown()
      throws ReflectiveOperationException {
    RedissonClient redisson = createRedissonClient();
    cleanup.deferCleanup(redisson::shutdown);
    redisson.getBucket("pool-metrics").set("value");

    String regularPool = "regular-" + endpoint;
    String subscriptionPool = "subscription-" + endpoint;
    assertConnectionPoolMetrics(regularPool, subscriptionPool);
    assertMetricNotEmitted(maxIdleMetricName());

    MasterSlaveEntry entry = getMasterSlaveEntry((Redisson) redisson);
    ConnectionsHolder<RedisConnection> holder = getMasterConnectionsHolder(entry);
    AsyncSemaphore semaphore = holder.getFreeConnectionsCounter();

    testing.clearData();
    RedisConnection connection = entry.connectionWriteOp(RedisCommands.PING).join();
    try {
      // Delta temporality reports one fewer idle connection and one used connection.
      assertUsageMetric(regularPool, -1, 1, subscriptionPool, 0, 0);
    } finally {
      entry.releaseWrite(connection);
    }

    testing.clearData();
    for (int i = 0; i < REGULAR_MAX; i++) {
      semaphore.acquire().join();
    }
    CompletableFuture<Void> queued = semaphore.acquire();
    try {
      assertPendingRequests(regularPool, 1, subscriptionPool, 0);
    } finally {
      for (int i = 0; i <= REGULAR_MAX; i++) {
        semaphore.release();
      }
    }
    queued.join();

    redisson.shutdown();
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

  private static void assertConnectionPoolMetrics(String regularPool, String subscriptionPool) {
    assertUsageMetric(regularPool, REGULAR_MIN_IDLE, 0, subscriptionPool, SUBSCRIPTION_MIN_IDLE, 0);
    assertPoolSizeMetric(
        "db.client.connection.idle.min",
        "db.client.connections.idle.min",
        "The minimum number of idle open connections allowed.",
        regularPool,
        REGULAR_MIN_IDLE,
        subscriptionPool,
        SUBSCRIPTION_MIN_IDLE);
    assertPoolSizeMetric(
        "db.client.connection.max",
        "db.client.connections.max",
        "The maximum number of open connections allowed.",
        regularPool,
        REGULAR_MAX,
        subscriptionPool,
        SUBSCRIPTION_MAX);
    assertPendingRequests(regularPool, 0, subscriptionPool, 0);
  }

  private static void assertUsageMetric(
      String regularPool,
      long regularIdle,
      long regularUsed,
      String subscriptionPool,
      long subscriptionIdle,
      long subscriptionUsed) {
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        usageMetricName(),
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasUnit(emitStableDatabaseSemconv() ? "{connection}" : "{connections}")
                        .hasDescription(
                            "The number of connections that are currently in state described by the state attribute.")
                        .hasLongSumSatisfying(
                            sum ->
                                sum.isNotMonotonic()
                                    .hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(regularIdle)
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(stringKey(poolNameKey()), regularPool),
                                                    equalTo(stringKey(stateKey()), "idle")),
                                        point ->
                                            point
                                                .hasValue(regularUsed)
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(stringKey(poolNameKey()), regularPool),
                                                    equalTo(stringKey(stateKey()), "used")),
                                        point ->
                                            point
                                                .hasValue(subscriptionIdle)
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(
                                                        stringKey(poolNameKey()), subscriptionPool),
                                                    equalTo(stringKey(stateKey()), "idle")),
                                        point ->
                                            point
                                                .hasValue(subscriptionUsed)
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(
                                                        stringKey(poolNameKey()), subscriptionPool),
                                                    equalTo(stringKey(stateKey()), "used"))))));
  }

  private static void assertPoolSizeMetric(
      String stableName,
      String legacyName,
      String description,
      String regularPool,
      long regularValue,
      String subscriptionPool,
      long subscriptionValue) {
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        emitStableDatabaseSemconv() ? stableName : legacyName,
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasUnit(emitStableDatabaseSemconv() ? "{connection}" : "{connections}")
                        .hasDescription(description)
                        .hasLongSumSatisfying(
                            sum ->
                                sum.isNotMonotonic()
                                    .hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(regularValue)
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(stringKey(poolNameKey()), regularPool)),
                                        point ->
                                            point
                                                .hasValue(subscriptionValue)
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(
                                                        stringKey(poolNameKey()),
                                                        subscriptionPool))))));
  }

  private static void assertPendingRequests(
      String regularPool, long regularPending, String subscriptionPool, long subscriptionPending) {
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        emitStableDatabaseSemconv()
            ? "db.client.connection.pending_requests"
            : "db.client.connections.pending_requests",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasUnit(emitStableDatabaseSemconv() ? "{request}" : "{requests}")
                        .hasDescription(
                            emitStableDatabaseSemconv()
                                ? "The number of current pending requests for an open connection."
                                : "The number of pending requests for an open connection, cumulative for the entire pool.")
                        .hasLongSumSatisfying(
                            sum ->
                                sum.isNotMonotonic()
                                    .hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(regularPending)
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(stringKey(poolNameKey()), regularPool)),
                                        point ->
                                            point
                                                .hasValue(subscriptionPending)
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(
                                                        stringKey(poolNameKey()),
                                                        subscriptionPool))))));
  }

  private static void assertMetricNotEmitted(String metricName) {
    assertThat(testing.metrics())
        .filteredOn(
            metric ->
                metric.getInstrumentationScopeInfo().getName().equals(INSTRUMENTATION_NAME)
                    && metric.getName().equals(metricName))
        .isEmpty();
  }

  private static MasterSlaveEntry getMasterSlaveEntry(Redisson redisson)
      throws ReflectiveOperationException {
    Field connectionManagerField = Redisson.class.getDeclaredField("connectionManager");
    connectionManagerField.setAccessible(true);
    ConnectionManager connectionManager = (ConnectionManager) connectionManagerField.get(redisson);
    return connectionManager.getEntrySet().iterator().next();
  }

  private static ConnectionsHolder<RedisConnection> getMasterConnectionsHolder(
      MasterSlaveEntry entry) throws ReflectiveOperationException {
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

  private static String poolNameKey() {
    return emitStableDatabaseSemconv() ? "db.client.connection.pool.name" : "pool.name";
  }

  private static String stateKey() {
    return emitStableDatabaseSemconv() ? "db.client.connection.state" : "state";
  }
}
