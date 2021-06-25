/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc

import java.sql.*
import java.util.logging.Logger

class TestDriver implements Driver {
  @Override
  Connection connect(String url, Properties info) throws SQLException {
    return new TestConnection()
  }

  @Override
  boolean acceptsURL(String url) throws SQLException {
    return url?.startsWith("jdbc:test:")
  }

  @Override
  DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
    return [new DriverPropertyInfo("test", "test")]
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
