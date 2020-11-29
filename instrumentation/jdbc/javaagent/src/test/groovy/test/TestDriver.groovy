/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test

import java.sql.Connection
import java.sql.Driver
import java.sql.DriverPropertyInfo
import java.sql.SQLException
import java.sql.SQLFeatureNotSupportedException
import java.util.logging.Logger

class TestDriver implements Driver {
  @Override
  Connection connect(String url, Properties info) throws SQLException {
    return new TestConnection("connectException=true" == url)
  }

  @Override
  boolean acceptsURL(String url) throws SQLException {
    return false
  }

  @Override
  DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
    return new DriverPropertyInfo[0]
  }

  @Override
  int getMajorVersion() {
    return 0
  }

  @Override
  int getMinorVersion() {
    return 0
  }

  @Override
  boolean jdbcCompliant() {
    return false
  }

  @Override
  Logger getParentLogger() throws SQLFeatureNotSupportedException {
    return null
  }
}
