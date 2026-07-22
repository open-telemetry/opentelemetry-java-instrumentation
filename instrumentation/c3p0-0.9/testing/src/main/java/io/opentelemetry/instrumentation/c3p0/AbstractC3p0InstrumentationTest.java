/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.c3p0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static org.assertj.core.api.Assertions.assertThat;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.mchange.v2.c3p0.PooledDataSource;
import io.opentelemetry.api.common.AttributeKey;
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
  private static final String DEFAULT_POOL_NAME = "c3p0";
  private static final AttributeKey<String> POOL_NAME_KEY =
      stringKey(emitStableDatabaseSemconv() ? "db.client.connection.pool.name" : "pool.name");

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
    c3p0DataSource.setDataSourceName("testPool");

    // when
    try (Connection connection = c3p0DataSource.getConnection()) {
      configure(c3p0DataSource);
    }

    // then
    String dataSourceName = c3p0DataSource.getDataSourceName();

    assertThat(dataSourceName).isNotEmpty();
    assertDataSourceMetrics(dataSourceName);

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

  @Test
  void shouldUseGeneratedDefaultPoolName() throws Exception {
    // given
    ComboPooledDataSource c3p0DataSource = new ComboPooledDataSource();
    c3p0DataSource.setDriverClass(MockDriver.class.getName());
    c3p0DataSource.setJdbcUrl("jdbc:mock:testDatabase");

    // when
    try (Connection connection = c3p0DataSource.getConnection()) {
      configure(c3p0DataSource);
      configure(c3p0DataSource);
    }

    // then
    String identityToken = c3p0DataSource.getIdentityToken();
    assertThat(c3p0DataSource.getDataSourceName()).isEqualTo(identityToken);
    assertGeneratedDefaultPoolName();

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

  private void assertGeneratedDefaultPoolName() {
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            emitStableDatabaseSemconv()
                ? "db.client.connection.count"
                : "db.client.connections.usage",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric.getLongSumData().getPoints())
                            .allSatisfy(
                                point ->
                                    assertThat(point.getAttributes().get(POOL_NAME_KEY))
                                        .matches(DEFAULT_POOL_NAME + "-\\d+"))));
  }

  private void assertDataSourceMetrics(String poolName) {
    DbConnectionPoolMetricsAssertions.create(testing(), INSTRUMENTATION_NAME, poolName)
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
