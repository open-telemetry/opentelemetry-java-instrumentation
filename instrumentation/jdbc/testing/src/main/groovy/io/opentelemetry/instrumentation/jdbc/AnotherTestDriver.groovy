/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc

import java.sql.*
import java.util.logging.Logger

class AnotherTestDriver implements Driver {
  @Override
  Connection connect(String url, Properties info) throws SQLException {
    return null
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
