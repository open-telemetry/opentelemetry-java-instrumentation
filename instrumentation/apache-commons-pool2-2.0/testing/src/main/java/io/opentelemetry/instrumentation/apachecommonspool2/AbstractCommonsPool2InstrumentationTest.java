/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachecommonspool2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.db.DbConnectionPoolMetricsAssertions;
import org.apache.commons.pool2.BaseKeyedPooledObjectFactory;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;
import org.apache.commons.pool2.impl.GenericKeyedObjectPoolConfig;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.jupiter.api.Test;

public abstract class AbstractCommonsPool2InstrumentationTest {

  protected static final String INSTRUMENTATION_NAME = "io.opentelemetry.apache-commons-pool2-2.0";

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

  private void testGenericObjectPoolMetrics(boolean jmxEnabled) throws Exception {
    GenericObjectPool<Object> pool = createGenericObjectPool("pool", jmxEnabled);
    Object borrowed = null;
    try {
      String poolName =
          "GenericObjectPool-"
              + (jmxEnabled
                  ? pool.getJmxName().getKeyProperty("name")
                  : String.valueOf(System.identityHashCode(pool)));
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
    GenericKeyedObjectPool<String, Object> pool = createGenericKeyedObjectPool("pool", jmxEnabled);
    Object borrowed = null;
    try {
      String poolName =
          "GenericKeyedObjectPool-"
              + (jmxEnabled
                  ? pool.getJmxName().getKeyProperty("name")
                  : String.valueOf(System.identityHashCode(pool)));
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

  private static GenericObjectPool<Object> createGenericObjectPool(
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
    DbConnectionPoolMetricsAssertions.create(testing(), INSTRUMENTATION_NAME, poolName)
        .disableConnectionTimeouts()
        .disableCreateTime()
        .disableWaitTime()
        .disableUseTime()
        .assertConnectionPoolEmitsMetrics();
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
