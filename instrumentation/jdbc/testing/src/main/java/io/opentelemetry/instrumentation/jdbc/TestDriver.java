/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

public class TestDriver implements Driver {
  @Override
  public Connection connect(String url, Properties info) throws SQLException {
    return new TestConnection();
  }

  @Override
  public boolean acceptsURL(String url) throws SQLException {
    return url.startsWith("jdbc:test:");
  }

  @Override
  public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
    DriverPropertyInfo driverPropertyInfo = new DriverPropertyInfo("test", "test");
    return new DriverPropertyInfo[] {driverPropertyInfo};
  }

  @Override
  public int getMajorVersion() {
    return 0;
  }

  @Override
  public int getMinorVersion() {
    return 0;
  }

  @Override
  public boolean jdbcCompliant() {
    return false;
  }

  @Override
  public Logger getParentLogger() throws SQLFeatureNotSupportedException {
    return null;
  }
}
