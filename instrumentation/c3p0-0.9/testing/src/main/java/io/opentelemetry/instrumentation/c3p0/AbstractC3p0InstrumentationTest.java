/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.c3p0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static org.assertj.core.api.Assertions.assertThat;

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
    // given
    ComboPooledDataSource c3p0DataSource = new ComboPooledDataSource();
    c3p0DataSource.setDriverClass(MockDriver.class.getName());
    c3p0DataSource.setJdbcUrl("jdbc:mock:testDatabase");

    // when
    try (Connection connection = c3p0DataSource.getConnection()) {
      configure(c3p0DataSource);
    }

    // then
    assertDataSourceMetrics(c3p0DataSource);

    // when
    shutdown(c3p0DataSource);
    c3p0DataSource.close();

    testing().clearData();

    // then
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

  private void assertDataSourceMetrics(PooledDataSource dataSource) {
    String dataSourceName = dataSource.getDataSourceName();

    assertThat(dataSourceName).isNotEmpty();

    DbConnectionPoolMetricsAssertions.create(
            testing(), INSTRUMENTATION_NAME, dataSource.getDataSourceName())
        .disableMinIdleConnections()
        .disableMaxIdleConnections()
        .disableMaxConnections()
        .disableConnectionTimeouts()
        .disableCreateTime()
        .disableWaitTime()
        .disableUseTime()
        .assertConnectionPoolEmitsMetrics();
  }
}
