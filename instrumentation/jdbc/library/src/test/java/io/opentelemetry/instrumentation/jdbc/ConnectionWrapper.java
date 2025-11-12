/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc;

import io.opentelemetry.instrumentation.jdbc.datasource.JdbcTelemetry;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;
import javax.sql.DataSource;

class ConnectionWrapper {

  // hack since JdbcTelemetry only supports wrapping data sources
  static Connection wrap(Connection connection, JdbcTelemetry telemetry) throws SQLException {
    DataSource dataSource = telemetry.wrap(new SingleConnectionDataSource(connection));
    return dataSource.getConnection();
  }

  private ConnectionWrapper() {}

  private static class SingleConnectionDataSource implements DataSource {
    private final Connection delegate;

    SingleConnectionDataSource(Connection delegate) {
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
