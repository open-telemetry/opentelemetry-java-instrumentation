/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.db;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;
import org.mockito.Mockito;

public final class MockDriver implements Driver {
  private static final MockDriver INSTANCE = new MockDriver();

  public static void register() throws SQLException {
    DriverManager.registerDriver(INSTANCE);
  }

  @Override
  public Connection connect(String url, Properties info) {
    return Mockito.mock(Connection.class);
  }

  @Override
  public boolean acceptsURL(String url) {
    return url.startsWith("jdbc:mock:");
  }

  @Override
  public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
    return new DriverPropertyInfo[0];
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
    throw new SQLFeatureNotSupportedException("Feature not supported");
  }
}
