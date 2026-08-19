/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.c3p0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.mchange.v2.c3p0.PooledDataSource;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.db.DbConnectionPoolMetricsAssertions;
import io.opentelemetry.instrumentation.testing.junit.db.MockDriver;
import java.sql.Connection;
import java.sql.SQLException;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public abstract class AbstractC3p0InstrumentationTest {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.c3p0-0.9";

  protected abstract InstrumentationExtension testing();

  protected abstract void configure(PooledDataSource dataSource) throws Exception;

  protected abstract void shutdown(PooledDataSource dataSource) throws Exception;

  @BeforeAll
  static void setUpMocks() throws SQLException {
    MockDriver.register();
  }

  @Test
  void shouldReportMetrics() throws Exception {
    ComboPooledDataSource dataSource = createDataSource("jdbc:mock:testDatabase");
    dataSource.setDataSourceName("testPool");

    try (Connection ignored = dataSource.getConnection()) {
      configure(dataSource);

      assertDataSourceMetrics("testPool");
    } finally {
      close(dataSource);
    }

    assertNoMetrics();
  }

  protected void assertDataSourceMetrics(String dataSourceName) {
    DbConnectionPoolMetricsAssertions.create(testing(), INSTRUMENTATION_NAME, dataSourceName)
        .disableMinIdleConnections()
        .disableMaxIdleConnections()
        .disableMaxConnections()
        .disableConnectionTimeouts()
        .disableCreateTime()
        .disableWaitTime()
        .disableUseTime()
        .assertConnectionPoolEmitsMetrics();
  }

  protected void assertNoMetrics() {
    testing().clearData();

    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            emitStableDatabaseSemconv()
                ? "db.client.connection.count"
                : "db.client.connections.usage",
            AbstractIterableAssert::isEmpty);
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            emitStableDatabaseSemconv()
                ? "db.client.connection.pending_requests"
                : "db.client.connections.pending_requests",
            AbstractIterableAssert::isEmpty);
  }

  protected static ComboPooledDataSource createDataSource(String jdbcUrl) throws Exception {
    ComboPooledDataSource dataSource = new ComboPooledDataSource();
    dataSource.setDriverClass(MockDriver.class.getName());
    dataSource.setJdbcUrl(jdbcUrl);
    return dataSource;
  }

  protected void close(PooledDataSource dataSource) throws Exception {
    shutdown(dataSource);
    dataSource.close();
  }
}
