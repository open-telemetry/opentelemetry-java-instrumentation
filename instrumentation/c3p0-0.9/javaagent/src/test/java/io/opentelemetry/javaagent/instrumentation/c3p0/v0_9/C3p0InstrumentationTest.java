/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.c3p0.v0_9;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.mchange.v2.c3p0.PooledDataSource;
import io.opentelemetry.instrumentation.c3p0.AbstractC3p0InstrumentationTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.sql.Connection;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class C3p0InstrumentationTest extends AbstractC3p0InstrumentationTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected void configure(PooledDataSource dataSource) {}

  @Override
  protected void shutdown(PooledDataSource dataSource) {}

  @Test
  void shouldUseJdbcUrlForDataSourceName() throws Exception {
    ComboPooledDataSource dataSource = createDataSource("jdbc:mock://db.example:5432/orders");

    assertDataSourceName(dataSource, "db.example:5432/orders");
  }

  @Test
  void shouldUseIpv6JdbcUrlForDataSourceName() throws Exception {
    ComboPooledDataSource dataSource = createDataSource("jdbc:mock://[2001:db8::1]:5432/orders");

    assertDataSourceName(dataSource, "[2001:db8::1]:5432/orders");
  }

  @Test
  void shouldUseConnectionPropertiesForDataSourceName() throws Exception {
    ComboPooledDataSource dataSource = createDataSource("jdbc:mock:ignored");
    Properties properties = new Properties();
    properties.setProperty("serverName", "properties.example");
    properties.setProperty("portNumber", "5433");
    properties.setProperty("databaseName", "inventory");
    dataSource.setProperties(properties);

    assertDataSourceName(dataSource, "properties.example:5433/inventory");
  }

  @Test
  void shouldUseFallbackDataSourceName() throws Exception {
    ComboPooledDataSource dataSource = createDataSource("jdbc:mock:testDatabase");

    assertDataSourceName(dataSource, "c3p0");
  }

  private void assertDataSourceName(ComboPooledDataSource dataSource, String expectedName)
      throws Exception {
    try (Connection ignored = dataSource.getConnection()) {
      assertDataSourceMetrics(expectedName);
    } finally {
      close(dataSource);
    }

    assertNoMetrics();
  }
}
