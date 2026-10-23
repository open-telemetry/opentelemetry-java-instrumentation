/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.oracleucp.v11_2;

import static org.mockito.Mockito.mock;

import java.sql.Connection;
import java.sql.SQLException;
import oracle.jdbc.pool.OracleDataSource;

public class TestOracleDataSource extends OracleDataSource {

  public TestOracleDataSource() throws SQLException {}

  @Override
  public Connection getConnection() {
    return mock(Connection.class);
  }

  @Override
  public Connection getConnection(String user, String password) {
    return mock(Connection.class);
  }
}
