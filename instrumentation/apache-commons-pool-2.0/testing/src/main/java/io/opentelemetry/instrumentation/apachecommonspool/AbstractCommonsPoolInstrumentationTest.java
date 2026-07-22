/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachecommonspool;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.assertj.LongPointAssert;
import io.opentelemetry.sdk.testing.assertj.LongSumAssert;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.apache.commons.pool2.BaseKeyedPooledObjectFactory;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;
import org.apache.commons.pool2.impl.GenericKeyedObjectPoolConfig;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.jupiter.api.Test;

public abstract class AbstractCommonsPoolInstrumentationTest {

  protected static final String INSTRUMENTATION_NAME = "io.opentelemetry.apache-commons-pool-2.0";
  private static final AttributeKey<String> POOL_NAME = stringKey("apache.commons_pool.pool.name");
  private static final AttributeKey<String> OBJECT_STATE =
      stringKey("apache.commons_pool.object.state");

  protected abstract InstrumentationExtension testing();

  protected abstract void configure(GenericObjectPool<?> pool, String poolName) throws Exception;

  protected abstract void configure(GenericKeyedObjectPool<?, ?> pool, String poolName)
      throws Exception;

  protected abstract void shutdown(GenericObjectPool<?> pool) throws Exception;

  protected abstract void shutdown(GenericKeyedObjectPool<?, ?> pool) throws Exception;

  @Test
  void shouldReportGenericObjectPoolMetrics() throws Exception {
    testGenericObjectPoolMetrics(true);
  }

  @Test
  void shouldReportGenericObjectPoolMetricsWhenJmxDisabled() throws Exception {
    testGenericObjectPoolMetrics(false);
  }

  @Test
  void shouldReusePoolNameAfterShutdown() throws Exception {
    String poolName = "GenericObjectPool-pool";
    GenericObjectPool<Object> first = createGenericObjectPool("pool", false);
    Object firstBorrowed = null;
    try {
      configure(first, poolName);

      firstBorrowed = first.borrowObject();

      assertPoolMetrics(poolName);
    } finally {
      if (firstBorrowed != null) {
        first.returnObject(firstBorrowed);
      }
      shutdown(first);
      first.close();
    }

    assertNoMetrics();

    GenericObjectPool<Object> second = createGenericObjectPool("pool", false);
    Object secondBorrowed = null;
    try {
      configure(second, poolName);

      secondBorrowed = second.borrowObject();

      assertPoolMetrics(poolName);
    } finally {
      if (secondBorrowed != null) {
        second.returnObject(secondBorrowed);
      }
      shutdown(second);
      second.close();
    }

    assertNoMetrics();
  }

  private void testGenericObjectPoolMetrics(boolean jmxEnabled) throws Exception {
    GenericObjectPool<Object> pool =
        createGenericObjectPool(jmxEnabled ? "objectPool" : "pool", jmxEnabled);
    Object borrowed = null;
    try {
      String poolName =
          "GenericObjectPool-" + (jmxEnabled ? pool.getJmxName().getKeyProperty("name") : "pool");
      configure(pool, poolName);

      borrowed = pool.borrowObject();

      assertPoolMetrics(poolName);
    } finally {
      if (borrowed != null) {
        pool.returnObject(borrowed);
      }
      shutdown(pool);
      pool.close();
    }

    assertNoMetrics();
  }

  @Test
  void shouldReportGenericKeyedObjectPoolMetrics() throws Exception {
    testGenericKeyedObjectPoolMetrics(true);
  }

  @Test
  void shouldReportGenericKeyedObjectPoolMetricsWhenJmxDisabled() throws Exception {
    testGenericKeyedObjectPoolMetrics(false);
  }

  private void testGenericKeyedObjectPoolMetrics(boolean jmxEnabled) throws Exception {
    GenericKeyedObjectPool<String, Object> pool =
        createGenericKeyedObjectPool(jmxEnabled ? "keyedObjectPool" : "pool", jmxEnabled);
    Object borrowed = null;
    try {
      String poolName =
          "GenericKeyedObjectPool-"
              + (jmxEnabled ? pool.getJmxName().getKeyProperty("name") : "pool");
      configure(pool, poolName);

      borrowed = pool.borrowObject("key");

      assertPoolMetrics(poolName);
    } finally {
      if (borrowed != null) {
        pool.returnObject("key", borrowed);
      }
      shutdown(pool);
      pool.close();
    }

    assertNoMetrics();
  }

  protected static GenericObjectPool<Object> createGenericObjectPool(
      String poolName, boolean jmxEnabled) {
    GenericObjectPoolConfig config = new GenericObjectPoolConfig();
    config.setJmxEnabled(jmxEnabled);
    config.setJmxNamePrefix(poolName);
    config.setMaxTotal(10);
    config.setMaxIdle(5);
    config.setMinIdle(1);
    return new GenericObjectPool<>(new TestObjectFactory(), config);
  }

  private static GenericKeyedObjectPool<String, Object> createGenericKeyedObjectPool(
      String poolName, boolean jmxEnabled) {
    GenericKeyedObjectPoolConfig config = new GenericKeyedObjectPoolConfig();
    config.setJmxEnabled(jmxEnabled);
    config.setJmxNamePrefix(poolName);
    config.setMaxTotal(10);
    config.setMaxTotalPerKey(5);
    config.setMaxIdlePerKey(3);
    config.setMinIdlePerKey(1);
    return new GenericKeyedObjectPool<>(new TestKeyedObjectFactory(), config);
  }

  private void assertPoolMetrics(String poolName) {
    verifyObjectCount(poolName);
    verifyMinIdleObjects(poolName);
    verifyMaxIdleObjects(poolName);
    verifyMaxObjects(poolName);
    verifyPendingRequests(poolName);
  }

  private void verifyObjectCount(String poolName) {
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "apache.commons_pool.object.count",
            metrics -> metrics.anySatisfy(metric -> verifyObjectCountMetric(metric, poolName)));
  }

  protected void assertObjectCountPoolNames(String... poolNames) {
    List<Consumer<LongPointAssert>> assertions = new ArrayList<>();
    for (String poolName : poolNames) {
      assertions.add(
          point ->
              point.hasAttributesSatisfying(
                  equalTo(POOL_NAME, poolName), equalTo(OBJECT_STATE, "idle")));
      assertions.add(
          point ->
              point.hasAttributesSatisfying(
                  equalTo(POOL_NAME, poolName), equalTo(OBJECT_STATE, "used")));
    }

    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "apache.commons_pool.object.count",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasLongSumSatisfying(
                                sum -> sum.isNotMonotonic().hasPointsSatisfying(assertions))));
  }

  private static void verifyObjectCountMetric(MetricData metric, String poolName) {
    assertThat(metric)
        .hasUnit("{object}")
        .hasDescription(
            "The number of objects currently in the state described by the state attribute.")
        .hasLongSumSatisfying(
            sum ->
                sum.isNotMonotonic()
                    .hasPointsSatisfying(
                        point ->
                            point.hasAttributesSatisfying(
                                equalTo(POOL_NAME, poolName), equalTo(OBJECT_STATE, "idle")),
                        point ->
                            point.hasAttributesSatisfying(
                                equalTo(POOL_NAME, poolName), equalTo(OBJECT_STATE, "used"))));
  }

  private void verifyMinIdleObjects(String poolName) {
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "apache.commons_pool.object.idle.min",
            metrics -> metrics.anySatisfy(metric -> verifyMinIdleObjectsMetric(metric, poolName)));
  }

  private static void verifyMinIdleObjectsMetric(MetricData metric, String poolName) {
    assertThat(metric)
        .hasUnit("{object}")
        .hasDescription("The minimum number of idle objects allowed in the pool.")
        .hasLongSumSatisfying(sum -> verifyPoolName(sum, poolName));
  }

  private void verifyMaxIdleObjects(String poolName) {
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "apache.commons_pool.object.idle.max",
            metrics -> metrics.anySatisfy(metric -> verifyMaxIdleObjectsMetric(metric, poolName)));
  }

  private static void verifyMaxIdleObjectsMetric(MetricData metric, String poolName) {
    assertThat(metric)
        .hasUnit("{object}")
        .hasDescription("The maximum number of idle objects allowed in the pool.")
        .hasLongSumSatisfying(sum -> verifyPoolName(sum, poolName));
  }

  private void verifyMaxObjects(String poolName) {
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "apache.commons_pool.object.max",
            metrics -> metrics.anySatisfy(metric -> verifyMaxObjectsMetric(metric, poolName)));
  }

  private static void verifyMaxObjectsMetric(MetricData metric, String poolName) {
    assertThat(metric)
        .hasUnit("{object}")
        .hasDescription("The maximum number of objects allowed in the pool.")
        .hasLongSumSatisfying(sum -> verifyPoolName(sum, poolName));
  }

  private void verifyPendingRequests(String poolName) {
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "apache.commons_pool.object.pending_requests",
            metrics -> metrics.anySatisfy(metric -> verifyPendingRequestsMetric(metric, poolName)));
  }

  private static void verifyPendingRequestsMetric(MetricData metric, String poolName) {
    assertThat(metric)
        .hasUnit("{request}")
        .hasDescription("The number of requests currently waiting for an object from the pool.")
        .hasLongSumSatisfying(sum -> verifyPoolName(sum, poolName));
  }

  private static void verifyPoolName(LongSumAssert sum, String poolName) {
    sum.isNotMonotonic()
        .hasPointsSatisfying(point -> point.hasAttributes(Attributes.of(POOL_NAME, poolName)));
  }

  protected void assertNoMetrics() {
    testing().clearData();

    await()
        .untilAsserted(
            () ->
                assertThat(testing().metrics())
                    .filteredOn(
                        metricData ->
                            metricData
                                .getInstrumentationScopeInfo()
                                .getName()
                                .equals(INSTRUMENTATION_NAME))
                    .isEmpty());
  }

  private static class TestObjectFactory extends BasePooledObjectFactory<Object> {

    @Override
    public Object create() {
      return new Object();
    }

    @Override
    public PooledObject<Object> wrap(Object testObject) {
      return new DefaultPooledObject<>(testObject);
    }
  }

  private static class TestKeyedObjectFactory extends BaseKeyedPooledObjectFactory<String, Object> {

    @Override
    public Object create(String key) {
      return new Object();
    }

    @Override
    public PooledObject<Object> wrap(Object value) {
      return new DefaultPooledObject<>(value);
    }
  }
}
