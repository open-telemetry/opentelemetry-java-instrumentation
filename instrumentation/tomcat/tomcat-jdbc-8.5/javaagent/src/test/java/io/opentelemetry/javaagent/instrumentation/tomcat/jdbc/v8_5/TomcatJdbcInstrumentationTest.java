/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.jdbc.v8_5;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.db.DbConnectionPoolMetricsAssertions;
import java.sql.Connection;
import java.sql.SQLException;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TomcatJdbcInstrumentationTest {

  private static final String DEFAULT_POOL_NAME = "tomcat-jdbc";

  @RegisterExtension
  static final AgentInstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Mock javax.sql.DataSource dataSourceMock;
  @Mock Connection connectionMock;

  @BeforeEach
  void setUp() throws SQLException {
    when(dataSourceMock.getConnection()).thenReturn(connectionMock);
  }

  @Test
  void shouldUseJdbcUrlForDefaultPoolName() throws SQLException {
    DataSource dataSource = newDataSource();
    dataSource.setUrl("jdbc:postgresql://db.example:5432/orders");

    assertConnectionPoolMetrics(dataSource, "db.example:5432/orders");
  }

  @Test
  void shouldUseConnectionPropertiesForDefaultPoolName() throws SQLException {
    DataSource dataSource = newDataSource();
    dataSource.setUrl("jdbc:postgresql:ignored");
    dataSource.setConnectionProperties(
        "serverName=properties.example;portNumber=5433;databaseName=inventory");

    assertConnectionPoolMetrics(dataSource, "properties.example:5433/inventory");
  }

  @Test
  void shouldUseServerAddressWhenPortAndNamespaceAreMissing() throws SQLException {
    DataSource dataSource = newDataSource();
    dataSource.setUrl("jdbc:custom:ignored");
    dataSource.setConnectionProperties("serverName=address-only.example");

    assertConnectionPoolMetrics(dataSource, "address-only.example");
  }

  @Test
  void shouldUseDbNamespaceWhenServerAddressIsMissing() throws SQLException {
    DataSource dataSource = newDataSource();
    dataSource.setUrl("jdbc:h2:mem:orders");

    assertConnectionPoolMetrics(dataSource, "orders");
  }

  @Test
  void shouldUseFixedPoolNameWhenUrlCannotBeParsed() throws SQLException {
    DataSource dataSource = newDataSource();

    // Calling createPool twice must continue to register one metric callback.
    dataSource.createPool();

    assertConnectionPoolMetrics(dataSource, DEFAULT_POOL_NAME);
  }

  @Test
  void shouldUseConfiguredPoolName() throws SQLException {
    DataSource dataSource = newDataSource();
    dataSource.setName("testPool");

    assertConnectionPoolMetrics(dataSource, "testPool");
  }

  @Test
  void shouldUseConfiguredPoolNameThatLooksLikeDefaultTomcatPoolName() throws SQLException {
    DataSource dataSource = newDataSource();
    String poolName =
        "Tomcat Connection Pool[orders-" + System.identityHashCode(PoolProperties.class) + "]";
    dataSource.setName(poolName);

    assertConnectionPoolMetrics(dataSource, poolName);
  }

  private DataSource newDataSource() {
    DataSource dataSource = new DataSource();
    dataSource.setDataSource(dataSourceMock);
    return dataSource;
  }

  private static void assertConnectionPoolMetrics(DataSource dataSource, String poolName)
      throws SQLException {
    dataSource.createPool();
    Connection connection = dataSource.getConnection();
    connection.close();
    assertConnectionPoolMetrics(poolName);
    dataSource.close();
    testing.clearData();
    assertNoConnectionPoolMetrics();
  }

  private static void assertConnectionPoolMetrics(String poolName) {
    assertThat(poolName).isNotEmpty();

    DbConnectionPoolMetricsAssertions.create(testing, "io.opentelemetry.tomcat-jdbc", poolName)
        // no timeouts happen during this test
        .disableConnectionTimeouts()
        .disableCreateTime()
        .disableWaitTime()
        .disableUseTime()
        .assertConnectionPoolEmitsMetrics();
  }

  private static void assertNoConnectionPoolMetrics() {
    testing.waitAndAssertMetrics(
        "io.opentelemetry.tomcat-jdbc",
        emitStableDatabaseSemconv() ? "db.client.connection.count" : "db.client.connections.usage",
        AbstractIterableAssert::isEmpty);
    testing.waitAndAssertMetrics(
        "io.opentelemetry.tomcat-jdbc",
        emitStableDatabaseSemconv()
            ? "db.client.connection.idle.min"
            : "db.client.connections.idle.min",
        AbstractIterableAssert::isEmpty);
    testing.waitAndAssertMetrics(
        "io.opentelemetry.tomcat-jdbc",
        emitStableDatabaseSemconv()
            ? "db.client.connection.idle.max"
            : "db.client.connections.idle.max",
        AbstractIterableAssert::isEmpty);
    testing.waitAndAssertMetrics(
        "io.opentelemetry.tomcat-jdbc",
        emitStableDatabaseSemconv() ? "db.client.connection.max" : "db.client.connections.max",
        AbstractIterableAssert::isEmpty);
    testing.waitAndAssertMetrics(
        "io.opentelemetry.tomcat-jdbc",
        emitStableDatabaseSemconv()
            ? "db.client.connection.pending_requests"
            : "db.client.connections.pending_requests",
        AbstractIterableAssert::isEmpty);
  }
}
