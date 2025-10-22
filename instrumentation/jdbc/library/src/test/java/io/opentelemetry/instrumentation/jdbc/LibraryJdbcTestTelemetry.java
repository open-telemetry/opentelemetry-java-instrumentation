/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc;

import io.opentelemetry.instrumentation.jdbc.datasource.JdbcTelemetry;
import io.opentelemetry.instrumentation.jdbc.datasource.OpenTelemetryDataSource;
import io.opentelemetry.instrumentation.jdbc.internal.OpenTelemetryConnection;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;
import javax.sql.DataSource;

final class LibraryJdbcTestTelemetry {

  private final InstrumentationExtension testing;
  private JdbcTelemetry telemetry;
  private JdbcTelemetry telemetryWithQueryParameters;

  LibraryJdbcTestTelemetry(InstrumentationExtension testing) {
    this.testing = testing;
  }

  Connection instrumentConnection(Connection connection) throws SQLException {
    return wrapConnection(connection, telemetry());
  }

  Connection instrumentConnectionWithQueryParameters(Connection connection) throws SQLException {
    return wrapConnection(connection, telemetryWithQueryParameters());
  }

  private static Connection wrapConnection(Connection connection, JdbcTelemetry telemetry)
      throws SQLException {
    if (connection instanceof OpenTelemetryConnection) {
      return connection;
    }
    DataSource dataSource = telemetry.wrap(new SingleConnectionDataSource(connection));
    return dataSource.getConnection();
  }

  DataSource instrumentDataSource(DataSource dataSource) {
    return wrapDataSource(dataSource, telemetry());
  }

  DataSource instrumentDataSourceWithQueryParameters(DataSource dataSource) {
    return wrapDataSource(dataSource, telemetryWithQueryParameters());
  }

  private static DataSource wrapDataSource(DataSource dataSource, JdbcTelemetry telemetry) {
    if (dataSource instanceof OpenTelemetryDataSource) {
      return dataSource;
    }
    return telemetry.wrap(dataSource);
  }

  private JdbcTelemetry telemetry() {
    if (telemetry == null) {
      telemetry =
          JdbcTelemetry.builder(testing.getOpenTelemetry())
              .setDataSourceInstrumenterEnabled(true)
              .setTransactionInstrumenterEnabled(true)
              .build();
    }
    return telemetry;
  }

  private JdbcTelemetry telemetryWithQueryParameters() {
    if (telemetryWithQueryParameters == null) {
      telemetryWithQueryParameters =
          JdbcTelemetry.builder(testing.getOpenTelemetry())
              .setDataSourceInstrumenterEnabled(true)
              .setTransactionInstrumenterEnabled(true)
              .setCaptureQueryParameters(true)
              .setStatementSanitizationEnabled(false)
              .build();
    }
    return telemetryWithQueryParameters;
  }

  private static final class SingleConnectionDataSource implements DataSource {
    private final Connection delegate;

    private SingleConnectionDataSource(Connection delegate) {
      this.delegate = delegate;
    }

    @Override
    public Connection getConnection() {
      return delegate;
    }

    @Override
    public Connection getConnection(String username, String password) {
      return getConnection();
    }

    @Override
    public PrintWriter getLogWriter() {
      return null;
    }

    @Override
    public void setLogWriter(PrintWriter out) {}

    @Override
    public void setLoginTimeout(int seconds) {}

    @Override
    public int getLoginTimeout() {
      return 0;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
      throw new SQLFeatureNotSupportedException();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
      if (iface.isInstance(delegate)) {
        return iface.cast(delegate);
      }
      throw new SQLFeatureNotSupportedException();
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
      return iface.isInstance(delegate);
    }
  }
}
