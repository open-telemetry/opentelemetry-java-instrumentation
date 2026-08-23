/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.jdbc.v8_5;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.db.DbConnectionPoolMetricsAssertions;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.stream.Stream;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

  @ParameterizedTest
  @MethodSource("derivedPoolNames")
  void shouldDerivePoolName(String url, String connectionProperties, String expectedPoolName)
      throws SQLException {
    DataSource dataSource = newDataSource();
    if (url != null) {
      dataSource.setUrl(url);
    }
    if (connectionProperties != null) {
      dataSource.setConnectionProperties(connectionProperties);
    }

    assertConnectionPoolMetrics(dataSource, expectedPoolName);
  }

  private static Stream<Arguments> derivedPoolNames() {
    return Stream.of(
        argumentSet(
            "jdbc url", "jdbc:postgresql://db.example:5432/orders", null, "db.example:5432/orders"),
        argumentSet(
            "ipv6 jdbc url",
            "jdbc:postgresql://[2001:db8::1]:5432/orders",
            null,
            "[2001:db8::1]:5432/orders"),
        argumentSet(
            "connection properties",
            "jdbc:postgresql:ignored",
            "serverName=properties.example;portNumber=5433;databaseName=inventory",
            "properties.example:5433/inventory"),
        argumentSet(
            "port and namespace missing",
            "jdbc:custom:ignored",
            "serverName=address-only.example",
            "address-only.example"),
        argumentSet("server address missing", "jdbc:h2:mem:orders", null, "orders"),
        argumentSet("url missing", null, null, DEFAULT_POOL_NAME));
  }

  @Test
  void shouldUseConfiguredPoolName() throws SQLException {
    DataSource dataSource = newDataSource();
    dataSource.setName("testPool");

    assertConnectionPoolMetrics(dataSource, "testPool");
  }

  @Test
  void shouldUseConfiguredPoolNameThatMatchesDefaultName() throws SQLException {
    DataSource dataSource = newDataSource();
    String poolName = dataSource.getPoolProperties().getName();

    dataSource.setName(poolName);

    assertConnectionPoolMetrics(dataSource, poolName);
  }

  @Test
  void shouldUseFixedPoolNameWhenConfiguredPoolNameIsEmpty() throws SQLException {
    DataSource dataSource = newDataSource();
    dataSource.setName("configured");
    dataSource.setName("");

    assertConnectionPoolMetrics(dataSource, DEFAULT_POOL_NAME);
  }

  private DataSource newDataSource() {
    DataSource dataSource = new DataSource();
    dataSource.setDataSource(dataSourceMock);
    return dataSource;
  }

  private static void assertConnectionPoolMetrics(DataSource dataSource, String poolName)
      throws SQLException {
    try {
      dataSource.createPool();
      dataSource.createPool();
      Connection connection = dataSource.getConnection();
      connection.close();
      assertConnectionPoolMetrics(poolName);
    } finally {
      dataSource.close();
      dataSource.close();
      testing.clearData();
    }
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
