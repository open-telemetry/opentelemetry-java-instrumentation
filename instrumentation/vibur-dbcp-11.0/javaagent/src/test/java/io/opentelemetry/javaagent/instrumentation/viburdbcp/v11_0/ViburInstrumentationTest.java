/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.viburdbcp.v11_0;

import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.viburdbcp.AbstractViburInstrumentationTest;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.vibur.dbcp.ViburDBCPDataSource;

class ViburInstrumentationTest extends AbstractViburInstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected void configure(ViburDBCPDataSource viburDataSource) {}

  @Override
  protected void shutdown(ViburDBCPDataSource viburDataSource) {}

  @ParameterizedTest
  @MethodSource("defaultPoolNameArguments")
  void shouldUseDerivedPoolName(
      String jdbcUrl, Properties driverProperties, String expectedPoolName) throws SQLException {
    ViburDBCPDataSource dataSource = newDataSource();
    if (jdbcUrl != null) {
      dataSource.setJdbcUrl(jdbcUrl);
    }
    if (driverProperties != null) {
      dataSource.setDriverProperties(driverProperties);
    }

    assertPoolName(dataSource, expectedPoolName);
  }

  private static Stream<Arguments> defaultPoolNameArguments() {
    Properties driverProperties = new Properties();
    driverProperties.setProperty("serverName", "properties.example");
    driverProperties.setProperty("portNumber", "5433");
    driverProperties.setProperty("databaseName", "inventory");

    return Stream.of(
        argumentSet(
            "JDBC URL", "jdbc:postgresql://db.example:5432/orders", null, "db.example:5432/orders"),
        argumentSet(
            "driver properties",
            "jdbc:postgresql:ignored",
            driverProperties,
            "properties.example:5433/inventory"),
        argumentSet("database namespace", "jdbc:h2:mem:orders", null, "orders"),
        argumentSet("fallback", null, null, "vibur-dbcp"));
  }

  @Test
  void shouldUseNameConfiguredThroughProperties() throws SQLException {
    Properties properties = new Properties();
    properties.setProperty("name", "propertiesPool");

    ViburDBCPDataSource dataSource = new ViburDBCPDataSource(properties);
    dataSource.setExternalDataSource(dataSourceMock);

    assertPoolName(dataSource, "propertiesPool");
  }

  @Test
  void shouldUseNameConfiguredThroughSetterWhenItMatchesGeneratedName() throws SQLException {
    ViburDBCPDataSource dataSource = newDataSource();
    String poolName = dataSource.getName();

    dataSource.setName(poolName);

    assertPoolName(dataSource, poolName);
  }

  @Test
  void shouldKeepConfiguredNameAfterRejectedInvalidRename() throws SQLException {
    ViburDBCPDataSource dataSource = newDataSource();
    dataSource.setName("configured");

    dataSource.setName("");

    assertPoolName(dataSource, "configured");
  }

  @Test
  void shouldUseNameConfiguredThroughPropertiesWhenItMatchesGeneratedName() throws SQLException {
    ViburDBCPDataSource previousDataSource = new ViburDBCPDataSource();
    String previousName = previousDataSource.getName();
    String poolName = "p" + (Integer.parseInt(previousName.substring(1)) + 1);

    Properties properties = new Properties();
    properties.setProperty("name", poolName);

    ViburDBCPDataSource dataSource = new ViburDBCPDataSource(properties);
    dataSource.setExternalDataSource(dataSourceMock);

    assertPoolName(dataSource, poolName);
  }

  @Test
  void shouldUseFallbackPoolNameAfterInvalidName() throws SQLException {
    ViburDBCPDataSource dataSource = newDataSource();
    dataSource.setName("");

    assertPoolName(dataSource, "vibur-dbcp");
  }

  private ViburDBCPDataSource newDataSource() {
    ViburDBCPDataSource dataSource = new ViburDBCPDataSource();
    dataSource.setExternalDataSource(dataSourceMock);
    return dataSource;
  }

  private void assertPoolName(ViburDBCPDataSource dataSource, String expectedPoolName)
      throws SQLException {
    when(dataSourceMock.getConnection()).thenReturn(connectionMock);
    dataSource.start();
    try (Connection unused = dataSource.getConnection()) {
      assertConnectionPoolEmitsMetrics(expectedPoolName);
    } finally {
      dataSource.close();
    }
  }
}
