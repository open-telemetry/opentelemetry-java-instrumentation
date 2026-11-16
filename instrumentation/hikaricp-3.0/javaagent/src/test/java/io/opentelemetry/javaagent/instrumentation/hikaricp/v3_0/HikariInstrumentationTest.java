/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hikaricp.v3_0;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.metrics.IMetricsTracker;
import com.zaxxer.hikari.metrics.MetricsTrackerFactory;
import io.opentelemetry.instrumentation.hikaricp.AbstractHikariInstrumentationTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class HikariInstrumentationTest extends AbstractHikariInstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected void configure(HikariConfig poolConfig, MetricsTrackerFactory userTracker) {
    if (userTracker != null) {
      poolConfig.setMetricsTrackerFactory(userTracker);
    }
  }

  @Test
  void shouldUseJdbcUrlForDefaultPoolNameWithCopiedConfig() throws SQLException {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl("jdbc:postgresql://db.example:5432/orders");
    config.setDataSource(dataSourceMock);

    assertPoolName(startDataSource(config), "orders");
  }

  @Test
  void shouldUseJdbcUrlForDefaultPoolNameAfterConfigWasValidated() throws SQLException {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl("jdbc:postgresql://db.example:5432/orders");
    config.setDataSource(dataSourceMock);
    config.validate();

    assertPoolName(startDataSource(config), "orders");
  }

  @Test
  void shouldUseJdbcUrlForDefaultPoolNameAfterFailedValidation() throws SQLException {
    HikariConfig config = new HikariConfig();
    assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class);

    config.setJdbcUrl("jdbc:postgresql://db.example:5432/orders");
    config.setDataSource(dataSourceMock);

    assertPoolName(startDataSource(config), "orders");
  }

  @Test
  void shouldKeepOriginalPoolNameForUserMetricsTracker() throws SQLException {
    AtomicReference<String> userMetricsPoolName = new AtomicReference<>();
    HikariDataSource dataSource = newDataSource();
    dataSource.setJdbcUrl("jdbc:postgresql://db.example:5432/orders");
    dataSource.setMetricsTrackerFactory(
        (poolName, poolStats) -> {
          userMetricsPoolName.set(poolName);
          return new IMetricsTracker() {};
        });
    startDataSource(dataSource);

    try {
      assertThat(userMetricsPoolName.get()).startsWith("HikariPool-");
      assertConnectionUsagePoolNames("orders");
    } finally {
      dataSource.close();
    }

    assertNoConnectionPoolMetrics();
  }

  @Test
  void shouldUseDataSourcePropertiesForDefaultPoolNameWithDataSourceClassName() {
    HikariConfig config = new HikariConfig();
    config.setDataSourceClassName("org.postgresql.ds.PGSimpleDataSource");
    config.addDataSourceProperty("serverName", "properties.example");
    config.addDataSourceProperty("portNumber", 5433);
    config.addDataSourceProperty("databaseName", "inventory");
    config.setMinimumIdle(0);
    config.setMaximumPoolSize(1);
    config.setInitializationFailTimeout(-1);

    assertPoolName(new HikariDataSource(config), "inventory");
  }

  @Test
  void shouldUseDbNamespaceWhenServerAddressIsMissing() throws SQLException {
    HikariDataSource dataSource = newDataSource();
    dataSource.setJdbcUrl("jdbc:h2:mem:orders");

    assertPoolName(startDataSource(dataSource), "orders");
  }

  @Test
  void shouldUseFixedNameWhenJdbcInformationIsMissing() throws SQLException {
    assertPoolName(startDataSource(newDataSource()), "hikaricp");
  }

  @Test
  void shouldUsePoolNameConfiguredThroughProperties() throws SQLException {
    Properties properties = new Properties();
    properties.setProperty("poolName", "propertiesPool");

    HikariConfig config = new HikariConfig(properties);
    config.setJdbcUrl("jdbc:postgresql://db.example:5432/orders");
    config.setDataSource(dataSourceMock);

    assertPoolName(startDataSource(config), "propertiesPool");
  }

  @Test
  void shouldUsePoolNameConfiguredAfterValidation() throws SQLException {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl("jdbc:postgresql://db.example:5432/orders");
    config.setDataSource(dataSourceMock);
    config.validate();
    config.setPoolName("configuredAfterValidation");

    assertPoolName(startDataSource(config), "configuredAfterValidation");
  }

  @Test
  void shouldNotChangeHikariPoolNameWhenMbeansAreRegistered() throws SQLException {
    HikariDataSource dataSource = newDataSource();
    dataSource.setJdbcUrl("jdbc:postgresql://db.example:5432/orders");
    dataSource.setRegisterMbeans(true);
    startDataSource(dataSource);

    try {
      assertThat(dataSource.getPoolName()).startsWith("HikariPool-");
      assertConnectionUsagePoolNames("orders");
    } finally {
      dataSource.close();
    }

    assertNoConnectionPoolMetrics();
  }

  @Test
  void shouldMergeDuplicateDerivedPoolNames() throws SQLException {
    HikariDataSource firstDataSource = newDataSource();
    firstDataSource.setJdbcUrl("jdbc:postgresql://db.example:5432/orders");

    HikariDataSource secondDataSource = newDataSource();
    secondDataSource.setJdbcUrl("jdbc:postgresql://db.example:5432/orders");

    startDataSource(firstDataSource);
    startDataSource(secondDataSource);

    try {
      assertConnectionUsagePoolNames("orders");
    } finally {
      firstDataSource.close();
      secondDataSource.close();
    }

    assertNoConnectionPoolMetrics();
  }

  @Test
  void contextDoesNotPropagateIntoMaintenanceTask() throws Exception {
    CountDownLatch threeCreations = new CountDownLatch(3);
    AtomicInteger counter = new AtomicInteger();
    DataSource source = mock(DataSource.class);
    when(source.getConnection())
        .thenAnswer(
            ignored -> {
              Connection connection = mock(Connection.class);
              when(connection.isValid(anyInt())).thenReturn(true);
              when(connection.getAutoCommit()).thenReturn(true);
              when(connection.getTransactionIsolation())
                  .thenReturn(Connection.TRANSACTION_READ_COMMITTED);
              threeCreations.countDown();
              if (counter.incrementAndGet() <= 3) {
                testing.runWithSpan("get connection", () -> {});
              }
              return connection;
            });

    // Disable validation so we could set a low max lifetime.
    HikariConfig config =
        new HikariConfig() {
          @Override
          public void validate() {}
        };
    config.setDataSource(source);
    config.setMinimumIdle(0);
    config.setMaximumPoolSize(1);
    config.setInitializationFailTimeout(-1);
    // Exercise Hikari's real lifetime scheduling, including its minimum accepted lifetime.
    config.setMaxLifetime(1_000);
    config.setConnectionTimeout(10_000);

    // Initialize the pool outside the request; only the borrow should capture request context.
    try (HikariDataSource pool = new HikariDataSource(config)) {
      testing.runWithSpan(
          "parent",
          () -> {
            try (Connection ignored = pool.getConnection()) {
              pool.setMinimumIdle(1);
            }
          });

      threeCreations.await(10, SECONDS);
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span -> span.hasName("get connection").hasParent(trace.getSpan(0))),
        trace ->
            trace.hasSpansSatisfyingExactly(span -> span.hasName("get connection").hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(span -> span.hasName("get connection").hasNoParent()));
  }

  private HikariDataSource newDataSource() {
    HikariDataSource dataSource = new HikariDataSource();
    dataSource.setDataSource(dataSourceMock);
    dataSource.setMinimumIdle(0);
    dataSource.setMaximumPoolSize(1);
    return dataSource;
  }

  private HikariDataSource startDataSource(HikariDataSource dataSource) throws SQLException {
    when(dataSourceMock.getConnection()).thenReturn(connectionMock);
    dataSource.getConnection().close();
    return dataSource;
  }

  private HikariDataSource startDataSource(HikariConfig config) throws SQLException {
    config.setMinimumIdle(0);
    config.setMaximumPoolSize(1);
    when(dataSourceMock.getConnection()).thenReturn(connectionMock);
    HikariDataSource dataSource = new HikariDataSource(config);
    dataSource.getConnection().close();
    return dataSource;
  }

  private void assertPoolName(HikariDataSource dataSource, String expectedPoolName) {
    try {
      assertConnectionUsagePoolNames(expectedPoolName);
    } finally {
      dataSource.close();
    }
    assertNoConnectionPoolMetrics();
  }
}
