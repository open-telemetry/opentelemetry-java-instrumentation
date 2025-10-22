/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc;

import io.opentelemetry.instrumentation.jdbc.datasource.JdbcTelemetry;
import io.opentelemetry.instrumentation.jdbc.datasource.JdbcTelemetryBuilder;
import io.opentelemetry.instrumentation.jdbc.datasource.internal.Experimental;
import io.opentelemetry.instrumentation.jdbc.testing.AbstractSqlCommenterTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.List;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.extension.RegisterExtension;

class SqlCommenterTest extends AbstractSqlCommenterTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected Connection createConnection(List<String> executedSql) throws SQLException {
    JdbcTelemetryBuilder builder = JdbcTelemetry.builder(testing.getOpenTelemetry());
    Experimental.setEnableSqlCommenter(builder, true);
    JdbcTelemetry telemetry = builder.build();
    DataSource dataSource = telemetry.wrap(new RecordingDataSource(executedSql));
    return dataSource.getConnection();
  }

  private static final class RecordingDataSource implements DataSource {
    private final List<String> executedSql;

    private RecordingDataSource(List<String> executedSql) {
      this.executedSql = executedSql;
    }

    @Override
    public Connection getConnection() {
      return new TestConnection(executedSql::add);
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
      throw new SQLFeatureNotSupportedException();
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
      return false;
    }
  }
}
