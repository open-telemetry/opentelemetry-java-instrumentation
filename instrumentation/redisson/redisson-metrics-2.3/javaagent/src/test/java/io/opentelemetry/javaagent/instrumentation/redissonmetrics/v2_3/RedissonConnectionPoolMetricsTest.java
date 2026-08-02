/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redissonmetrics.v2_3;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.javaagent.instrumentation.redissonmetrics.v2_3.RedissonConnectionPoolMetrics.INSTRUMENTATION_NAME;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.util.Objects.requireNonNull;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.redisson.Redisson;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.redisson.connection.ClientConnectionsEntry;
import org.redisson.pubsub.AsyncSemaphore;
import org.testcontainers.containers.GenericContainer;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedissonConnectionPoolMetricsTest {

  private static final int REGULAR_MIN_IDLE = 5;
  private static final int REGULAR_MAX = 10;
  private static final int SUBSCRIPTION_MIN_IDLE = 2;
  private static final int SUBSCRIPTION_MAX = 4;

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

      assertConnectionUsageValues();
      assertPoolSizeMetric(
          "db.client.connection.idle.min",
          "db.client.connections.idle.min",
          "The minimum number of idle open connections allowed.",
          REGULAR_MIN_IDLE,
          SUBSCRIPTION_MIN_IDLE);
      assertPoolSizeMetric(
          "db.client.connection.max",
          "db.client.connections.max",
          "The maximum number of open connections allowed.",
          REGULAR_MAX,
          SUBSCRIPTION_MAX);
      if (usesAsyncSemaphore()) {
        assertPendingRequests();
      } else {
        assertNoPendingRequests();
      }

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

  @Test
  void shouldReadPendingRequestsAfterListenerRemoval() throws ReflectiveOperationException {
    AsyncSemaphore semaphore = new AsyncSemaphore(0);
    Supplier<Integer> pendingRequests =
        requireNonNull(RedissonConnectionPoolAccessor.pendingRequestsSupplier(semaphore));
    Runnable listener = () -> {};

    assertThat(pendingRequests.get()).isZero();
    semaphore.acquire(listener);
    assertThat(pendingRequests.get()).isEqualTo(1);

    try {
      semaphore.getClass().getMethod("remove", Runnable.class).invoke(semaphore, listener);
    } catch (NoSuchMethodException ignored) {
      // 3.16.1+ no longer exposes remove(); release drains the queued listener.
      semaphore.release();
    }
    assertThat(pendingRequests.get()).isZero();
  }

  private RedissonClient createRedissonClient() throws ReflectiveOperationException {
    String address = redisAddress();
    if (usesAsyncSemaphore()) {
      address = "redis://" + address;
    }

    Config config = new Config();
    config.setCodec(StringCodec.INSTANCE);
    SingleServerConfig singleServerConfig = config.useSingleServer();
    singleServerConfig.setAddress(address);
    singleServerConfig.setTimeout(30_000);
    singleServerConfig.setConnectionPoolSize(REGULAR_MAX);
    singleServerConfig.setConnectionMinimumIdleSize(REGULAR_MIN_IDLE);
    singleServerConfig.setSubscriptionConnectionPoolSize(SUBSCRIPTION_MAX);
    singleServerConfig.setSubscriptionConnectionMinimumIdleSize(SUBSCRIPTION_MIN_IDLE);
    try {
      singleServerConfig
          .getClass()
          .getMethod("setPingConnectionInterval", int.class)
          .invoke(singleServerConfig, 0);
    } catch (ReflectiveOperationException ignored) {
      // ignored
    }
    return Redisson.create(config);
  }

  private void assertConnectionUsageValues() {
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        emitStableDatabaseSemconv() ? "db.client.connection.count" : "db.client.connections.usage",
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
                                                .hasValue(REGULAR_MIN_IDLE)
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(
                                                        stringKey(poolNameKey()),
                                                        expectedRegularPoolName()),
                                                    equalTo(stringKey(stateKey()), "idle")),
                                        point ->
                                            point
                                                .hasValue(0)
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(
                                                        stringKey(poolNameKey()),
                                                        expectedRegularPoolName()),
                                                    equalTo(stringKey(stateKey()), "used")),
                                        point ->
                                            point
                                                .hasValue(SUBSCRIPTION_MIN_IDLE)
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(
                                                        stringKey(poolNameKey()),
                                                        expectedSubscriptionPoolName()),
                                                    equalTo(stringKey(stateKey()), "idle")),
                                        point ->
                                            point
                                                .hasValue(0)
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(
                                                        stringKey(poolNameKey()),
                                                        expectedSubscriptionPoolName()),
                                                    equalTo(stringKey(stateKey()), "used"))))));
  }

  private void assertPoolSizeMetric(
      String stableName,
      String oldName,
      String description,
      int regularValue,
      int subscriptionValue) {
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        emitStableDatabaseSemconv() ? stableName : oldName,
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
                                                    equalTo(
                                                        stringKey(poolNameKey()),
                                                        expectedRegularPoolName())),
                                        point ->
                                            point
                                                .hasValue(subscriptionValue)
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(
                                                        stringKey(poolNameKey()),
                                                        expectedSubscriptionPoolName()))))));
  }

  private void assertPendingRequests() {
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
                                                .hasValue(0)
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(
                                                        stringKey(poolNameKey()),
                                                        expectedRegularPoolName())),
                                        point ->
                                            point
                                                .hasValue(0)
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(
                                                        stringKey(poolNameKey()),
                                                        expectedSubscriptionPoolName()))))));
  }

  private static void assertNoPendingRequests() {
    String pendingRequestsMetricName =
        emitStableDatabaseSemconv()
            ? "db.client.connection.pending_requests"
            : "db.client.connections.pending_requests";
    assertThat(testing.metrics())
        .filteredOn(
            metricData ->
                metricData.getInstrumentationScopeInfo().getName().equals(INSTRUMENTATION_NAME)
                    && metricData.getName().equals(pendingRequestsMetricName))
        .isEmpty();
  }

  private static boolean usesAsyncSemaphore() throws NoSuchFieldException {
    return ClientConnectionsEntry.class
        .getDeclaredField("freeConnectionsCounter")
        .getType()
        .getName()
        .equals("org.redisson.pubsub.AsyncSemaphore");
  }

  private static String poolNameKey() {
    return emitStableDatabaseSemconv() ? "db.client.connection.pool.name" : "pool.name";
  }

  private static String stateKey() {
    return emitStableDatabaseSemconv() ? "db.client.connection.state" : "state";
  }

  private String expectedRegularPoolName() {
    return "master-regular-" + redisAddress();
  }

  private String expectedSubscriptionPoolName() {
    return "master-subscription-" + redisAddress();
  }

  private String redisAddress() {
    return "localhost:" + redisServer.getMappedPort(6379);
  }
}
