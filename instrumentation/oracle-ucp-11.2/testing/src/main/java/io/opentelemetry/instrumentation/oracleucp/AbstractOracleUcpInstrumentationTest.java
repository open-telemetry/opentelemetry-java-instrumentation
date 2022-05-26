/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oracleucp;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.db.DbConnectionPoolMetricsAssertions;
import io.opentelemetry.instrumentation.testing.junit.db.MockDriver;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import oracle.ucp.admin.UniversalConnectionPoolManagerImpl;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public abstract class AbstractOracleUcpInstrumentationTest {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.orcale-ucp-11.2";

  protected abstract InstrumentationExtension testing();

  protected abstract void configure(PoolDataSource connectionPool) throws Exception;

  protected abstract void shutdown(PoolDataSource connectionPool) throws Exception;

  @BeforeAll
  static void setUpMocks() throws SQLException {
    MockDriver.register();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldReportMetrics(boolean setExplicitPoolName) throws Exception {
    // given
    PoolDataSource connectionPool = PoolDataSourceFactory.getPoolDataSource();
    connectionPool.setConnectionFactoryClassName(MockDriver.class.getName());
    connectionPool.setURL("jdbc:mock:testDatabase");
    if (setExplicitPoolName) {
      connectionPool.setConnectionPoolName("testPool");
    }

    // when
    Connection connection = connectionPool.getConnection();
    configure(connectionPool);
    TimeUnit.MILLISECONDS.sleep(100);
    connection.close();

    // then
    DbConnectionPoolMetricsAssertions.create(
            testing(), INSTRUMENTATION_NAME, connectionPool.getConnectionPoolName())
        .disableMinIdleConnections()
        .disableMaxIdleConnections()
        .disableConnectionTimeouts()
        .disableCreateTime()
        .disableWaitTime()
        .disableUseTime()
        .assertConnectionPoolEmitsMetrics();

    // when
    // this one too shouldn't cause any problems when called more than once
    connectionPool.getConnection().close();
    connectionPool.getConnection().close();

    shutdown(connectionPool);
    UniversalConnectionPoolManagerImpl.getUniversalConnectionPoolManager()
        .destroyConnectionPool(connectionPool.getConnectionPoolName());

    // sleep exporter interval
    Thread.sleep(100);
    testing().clearData();
    Thread.sleep(100);

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME, "db.client.connections.usage", AbstractIterableAssert::isEmpty);
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME, "db.client.connections.max", AbstractIterableAssert::isEmpty);
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "db.client.connections.pending_requests",
            AbstractIterableAssert::isEmpty);
  }
}
