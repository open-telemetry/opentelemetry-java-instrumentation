/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.oracleucp.v11_2;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.db.DbConnectionPoolMetricsAssertions;
import io.opentelemetry.instrumentation.testing.junit.db.MockDriver;
import oracle.ucp.UniversalConnectionPool;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import oracle.ucp.jdbc.PoolDataSourceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class OracleUcpPoolNameTest {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.oracle-ucp-11.2";
  private static final AttributeKey<String> POOL_NAME_KEY =
      AttributeKey.stringKey(
          emitStableDatabaseSemconv() ? "db.client.connection.pool.name" : "pool.name");
  private static final String CONNECTION_USAGE_METRIC_NAME =
      emitStableDatabaseSemconv() ? "db.client.connection.count" : "db.client.connections.usage";

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void shouldDerivePoolNameFromOracleJdbcUrl() throws Exception {
    PoolDataSource connectionPool = createPool("jdbc:oracle:thin:@//db.example:1522/orders");
    UniversalConnectionPool universalConnectionPool = startPool(connectionPool);

    try {
      assertPoolMetrics("db.example:1522/orders");
      assertThat(connectionPool.getConnectionPoolName()).isNotEqualTo("db.example:1522/orders");
    } finally {
      universalConnectionPool.stop();
    }

    assertNoConnectionPoolMetrics();
  }

  @Test
  void shouldUseFallbackWhenJdbcInformationIsMissing() throws Exception {
    PoolDataSource connectionPool = createPool("jdbc:oracle:oci8:@");
    UniversalConnectionPool universalConnectionPool = startPool(connectionPool);

    try {
      assertPoolMetrics("oracle-ucp");
    } finally {
      universalConnectionPool.stop();
    }

    assertNoConnectionPoolMetrics();
  }

  @Test
  void shouldDerivePoolNameFromConnectionProperties() throws Exception {
    PoolDataSource connectionPool = createPool("jdbc:oracle:oci8:@");
    connectionPool.setConnectionProperty("serverName", "properties.example");
    connectionPool.setConnectionProperty("portNumber", "1523");
    connectionPool.setConnectionProperty("databaseName", "inventory");
    UniversalConnectionPool universalConnectionPool = startPool(connectionPool);

    try {
      assertPoolMetrics("properties.example:1523/inventory");
    } finally {
      universalConnectionPool.stop();
    }

    assertNoConnectionPoolMetrics();
  }

  @Test
  void shouldDerivePoolNameFromConnectionPropertiesWithoutJdbcUrl() throws Exception {
    assumeFalse(testLatestDeps(), "UCP latest requires a JDBC URL");

    PoolDataSource connectionPool = createPoolWithoutJdbcUrl();
    connectionPool.setConnectionProperty("serverName", "properties.example");
    connectionPool.setConnectionProperty("portNumber", "1523");
    connectionPool.setConnectionProperty("databaseName", "inventory");
    UniversalConnectionPool universalConnectionPool = startPool(connectionPool);

    try {
      assertPoolMetrics("properties.example:1523/inventory");
    } finally {
      universalConnectionPool.stop();
    }

    assertNoConnectionPoolMetrics();
  }

  @Test
  void shouldDerivePoolNameFromPoolDataSourcePropertiesWithoutJdbcUrl() throws Exception {
    assumeFalse(testLatestDeps(), "UCP latest requires a JDBC URL");

    PoolDataSource connectionPool = createPoolWithoutJdbcUrl();
    connectionPool.setConnectionProperty("serverName", "properties.example");
    connectionPool.setConnectionProperty("portNumber", "1523");
    connectionPool.setConnectionProperty("databaseName", "inventory");
    connectionPool.setServerName("setters.example");
    connectionPool.setPortNumber(1524);
    connectionPool.setDatabaseName("billing");
    UniversalConnectionPool universalConnectionPool = startPool(connectionPool);

    try {
      assertPoolMetrics("setters.example:1524/billing");
    } finally {
      universalConnectionPool.stop();
    }

    assertNoConnectionPoolMetrics();
  }

  @Test
  void shouldRetainDerivedPoolNameAfterRestart() throws Exception {
    PoolDataSource connectionPool = createPool("jdbc:oracle:thin:@//db.example:1522/orders");
    UniversalConnectionPool universalConnectionPool = startPool(connectionPool);

    try {
      assertPoolMetrics("db.example:1522/orders");

      universalConnectionPool.stop();
      testing.clearData();
      universalConnectionPool.start();

      assertPoolMetrics("db.example:1522/orders");
    } finally {
      universalConnectionPool.stop();
    }

    assertNoConnectionPoolMetrics();
  }

  @Test
  void shouldUseExplicitPoolNameAfterRename() throws Exception {
    PoolDataSource connectionPool = createPool("jdbc:oracle:thin:@//db.example:1522/orders");
    UniversalConnectionPool universalConnectionPool = startPool(connectionPool);

    try {
      assertPoolMetrics("db.example:1522/orders");

      universalConnectionPool.stop();
      testing.clearData();
      universalConnectionPool.setName("renamedPool");
      universalConnectionPool.start();

      assertPoolMetrics("renamedPool");
    } finally {
      universalConnectionPool.stop();
    }

    assertNoConnectionPoolMetrics();
  }

  @Test
  void shouldMergeDuplicateDerivedPoolNamesWithoutChangingUcpNames() throws Exception {
    PoolDataSource firstPool = createPool("jdbc:oracle:thin:@//db.example:1522/orders");
    PoolDataSource secondPool = createPool("jdbc:oracle:thin:@//db.example:1522/orders");

    UniversalConnectionPool firstConnectionPool = startPool(firstPool);
    UniversalConnectionPool secondConnectionPool = startPool(secondPool);

    try {
      assertThat(firstPool.getConnectionPoolName())
          .isNotEqualTo(secondPool.getConnectionPoolName());
      assertConnectionUsagePoolNames("db.example:1522/orders");
    } finally {
      firstConnectionPool.stop();
      secondConnectionPool.stop();
    }

    assertNoConnectionPoolMetrics();
  }

  private static PoolDataSource createPoolWithoutJdbcUrl() throws Exception {
    PoolDataSource connectionPool = PoolDataSourceFactory.getPoolDataSource();
    connectionPool.setConnectionFactoryClassName(TestOracleDataSource.class.getName());
    connectionPool.setInitialPoolSize(0);
    connectionPool.setMinPoolSize(0);
    return connectionPool;
  }

  private static PoolDataSource createPool(String jdbcUrl) throws Exception {
    PoolDataSource connectionPool = PoolDataSourceFactory.getPoolDataSource();
    connectionPool.setConnectionFactoryClassName(MockDriver.class.getName());
    connectionPool.setURL(jdbcUrl);
    return connectionPool;
  }

  private static UniversalConnectionPool startPool(PoolDataSource connectionPool) throws Exception {
    UniversalConnectionPool universalConnectionPool =
        ((PoolDataSourceImpl) connectionPool).createUniversalConnectionPool();
    universalConnectionPool.start();
    return universalConnectionPool;
  }

  private static void assertPoolMetrics(String poolName) {
    DbConnectionPoolMetricsAssertions.create(testing, INSTRUMENTATION_NAME, poolName)
        .disableMinIdleConnections()
        .disableMaxIdleConnections()
        .disableConnectionTimeouts()
        .disableCreateTime()
        .disableWaitTime()
        .disableUseTime()
        .assertConnectionPoolEmitsMetrics();
  }

  private static void assertConnectionUsagePoolNames(String... poolNames) {
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        CONNECTION_USAGE_METRIC_NAME,
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(
                            metric.getLongSumData().getPoints().stream()
                                .map(point -> point.getAttributes().get(POOL_NAME_KEY))
                                .collect(toSet()))
                        .containsExactlyInAnyOrder(poolNames)));
  }

  private static void assertNoConnectionPoolMetrics() {
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
}
