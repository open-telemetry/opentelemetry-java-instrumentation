/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redissonmetrics;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.redisson.Redisson;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveEntry;
import org.testcontainers.containers.GenericContainer;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractRedissonConnectionPoolMetricsTest {

  private static final int REDIS_PORT = 6379;
  protected static final int REGULAR_MIN_IDLE = 5;
  protected static final int REGULAR_MAX = 10;
  protected static final int SUBSCRIPTION_MIN_IDLE = 2;
  protected static final int SUBSCRIPTION_MAX = 4;

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

  protected abstract String instrumentationName();

  protected abstract void assertDynamicPoolMetrics(
      Redisson redisson, String regularPool, String subscriptionPool)
      throws ReflectiveOperationException;

  @Test
  void reportsRegularAndSubscriptionPoolMetricsUntilClientShutdown()
      throws ReflectiveOperationException {
    Redisson redisson = createRedissonClient();
    cleanup.deferCleanup(redisson::shutdown);
    redisson.getBucket("pool-metrics").set("value");

    String regularPool = "regular-" + endpoint;
    String subscriptionPool = "subscription-" + endpoint;
    assertConnectionPoolMetrics(regularPool, subscriptionPool);
    assertMetricNotEmitted(maxIdleMetricName());

    assertDynamicPoolMetrics(redisson, regularPool, subscriptionPool);

    redisson.shutdown();
    clearMetrics();

    await()
        .untilAsserted(
            () ->
                assertThat(testing.metrics())
                    .filteredOn(
                        metric ->
                            metric
                                .getInstrumentationScopeInfo()
                                .getName()
                                .equals(instrumentationName()))
                    .isEmpty());
  }

  private Redisson createRedissonClient() {
    Config config = new Config();
    config.setCodec(StringCodec.INSTANCE);
    SingleServerConfig serverConfig = config.useSingleServer();

    serverConfig
        .setAddress("redis://" + endpoint)
        .setTimeout(30_000)
        .setConnectionMinimumIdleSize(REGULAR_MIN_IDLE)
        .setConnectionPoolSize(REGULAR_MAX)
        .setSubscriptionConnectionMinimumIdleSize(SUBSCRIPTION_MIN_IDLE)
        .setSubscriptionConnectionPoolSize(SUBSCRIPTION_MAX)
        .setPingConnectionInterval(0);
    return (Redisson) Redisson.create(config);
  }

  private void assertConnectionPoolMetrics(String regularPool, String subscriptionPool) {
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

  protected final void clearMetrics() {
    testing.clearData();
  }

  protected final void assertUsageMetric(
      String regularPool,
      long regularIdle,
      long regularUsed,
      String subscriptionPool,
      long subscriptionIdle,
      long subscriptionUsed) {
    testing.waitAndAssertMetrics(
        instrumentationName(),
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

  private void assertPoolSizeMetric(
      String stableName,
      String legacyName,
      String description,
      String regularPool,
      long regularValue,
      String subscriptionPool,
      long subscriptionValue) {
    testing.waitAndAssertMetrics(
        instrumentationName(),
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

  protected final void assertPendingRequests(
      String regularPool, long regularPending, String subscriptionPool, long subscriptionPending) {
    testing.waitAndAssertMetrics(
        instrumentationName(),
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

  protected static MasterSlaveEntry getMasterSlaveEntry(Redisson redisson)
      throws ReflectiveOperationException {
    Field connectionManagerField = Redisson.class.getDeclaredField("connectionManager");
    connectionManagerField.setAccessible(true);
    ConnectionManager connectionManager = (ConnectionManager) connectionManagerField.get(redisson);
    return connectionManager.getEntrySet().iterator().next();
  }

  protected static Object getMasterConnectionsEntry(Object masterSlaveEntry)
      throws ReflectiveOperationException {
    Field masterEntryField = MasterSlaveEntry.class.getDeclaredField("masterEntry");
    masterEntryField.setAccessible(true);
    return masterEntryField.get(masterSlaveEntry);
  }

  private void assertMetricNotEmitted(String metricName) {
    assertThat(testing.metrics())
        .filteredOn(
            metric ->
                metric.getInstrumentationScopeInfo().getName().equals(instrumentationName())
                    && metric.getName().equals(metricName))
        .isEmpty();
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
