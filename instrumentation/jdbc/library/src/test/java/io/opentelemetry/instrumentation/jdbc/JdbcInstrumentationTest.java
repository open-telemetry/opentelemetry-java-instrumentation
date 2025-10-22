/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc;

import io.opentelemetry.instrumentation.jdbc.testing.AbstractJdbcInstrumentationTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.provider.Arguments;

class JdbcInstrumentationTest extends AbstractJdbcInstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private static final LibraryJdbcTestTelemetry telemetryHelper =
      new LibraryJdbcTestTelemetry(testing);

  private static final String DB_NAME = "jdbcUnitTest";
  private static final String DB_NAME_LOWER = DB_NAME.toLowerCase(Locale.ROOT);

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected Connection instrumentConnection(Connection connection) throws SQLException {
    return telemetryHelper.instrumentConnection(connection);
  }

  @Override
  protected DataSource instrumentDataSource(DataSource dataSource) {
    return telemetryHelper.instrumentDataSource(dataSource);
  }

  @Override
  protected String expectedGetConnectionSpanName(
      DataSource originalDatasource, DataSource instrumentedDatasource) {
    return originalDatasource.getClass().getSimpleName();
  }

  @Override
  protected Class<?> expectedGetConnectionCodeClass(
      DataSource originalDatasource, DataSource instrumentedDatasource) {
    return originalDatasource.getClass();
  }

  static Stream<Arguments> connectionConstructorStream() {
    return Stream.of(
        Arguments.of(
            true,
            "h2",
            wrapDriver(new org.h2.Driver()),
            "jdbc:h2:mem:" + DB_NAME,
            null,
            "SELECT 3;",
            "SELECT ?;",
            "SELECT " + DB_NAME_LOWER,
            "h2:mem:",
            null),
        Arguments.of(
            true,
            "derby",
            wrapDriver(new org.apache.derby.jdbc.EmbeddedDriver()),
            "jdbc:derby:memory:" + DB_NAME + ";create=true",
            "APP",
            "SELECT 3 FROM SYSIBM.SYSDUMMY1",
            "SELECT ? FROM SYSIBM.SYSDUMMY1",
            "SELECT SYSIBM.SYSDUMMY1",
            "derby:memory:",
            "SYSIBM.SYSDUMMY1"),
        Arguments.of(
            false,
            "h2",
            wrapDriver(new org.h2.Driver()),
            "jdbc:h2:mem:" + DB_NAME,
            null,
            "SELECT 3;",
            "SELECT ?;",
            "SELECT " + DB_NAME_LOWER,
            "h2:mem:",
            null),
        Arguments.of(
            false,
            "derby",
            wrapDriver(new org.apache.derby.jdbc.EmbeddedDriver()),
            "jdbc:derby:memory:" + DB_NAME + ";create=true",
            "APP",
            "SELECT 3 FROM SYSIBM.SYSDUMMY1",
            "SELECT ? FROM SYSIBM.SYSDUMMY1",
            "SELECT SYSIBM.SYSDUMMY1",
            "derby:memory:",
            "SYSIBM.SYSDUMMY1"));
  }

  private static Driver wrapDriver(Driver delegate) {
    return new Driver() {
      @Override
      public Connection connect(String url, Properties info) throws SQLException {
        Connection connection = delegate.connect(url, info);
        if (connection == null) {
          return null;
        }
        return telemetryHelper.instrumentConnection(connection);
      }

      @Override
      public boolean acceptsURL(String url) throws SQLException {
        return delegate.acceptsURL(url);
      }

      @Override
      public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return delegate.getPropertyInfo(url, info);
      }

      @Override
      public int getMajorVersion() {
        return delegate.getMajorVersion();
      }

      @Override
      public int getMinorVersion() {
        return delegate.getMinorVersion();
      }

      @Override
      public boolean jdbcCompliant() {
        return delegate.jdbcCompliant();
      }

      @Override
      public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return delegate.getParentLogger();
      }
    };
  }
}
