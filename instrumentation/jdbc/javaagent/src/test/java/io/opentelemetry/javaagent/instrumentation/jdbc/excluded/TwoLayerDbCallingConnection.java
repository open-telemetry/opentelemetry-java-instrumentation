/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc.excluded;

import io.opentelemetry.instrumentation.jdbc.TestConnection;
import io.opentelemetry.instrumentation.jdbc.TestPreparedStatement;
import io.opentelemetry.instrumentation.jdbc.TestStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

class TwoLayerDbCallingConnection extends TestConnection {
  final Connection inner;

  TwoLayerDbCallingConnection(Connection inner) {
    this.inner = inner;
  }

  @Override
  public Statement createStatement() throws SQLException {
    return new TestStatement(this) {
      @Override
      public ResultSet executeQuery(String sql) throws SQLException {
        return inner.createStatement().executeQuery(sql);
      }
    };
  }

  @Override
  public PreparedStatement prepareStatement(String sql) throws SQLException {
    return new TestPreparedStatement(this) {
      @Override
      public ResultSet executeQuery() throws SQLException {
        return inner.prepareStatement(sql).executeQuery(sql);
      }
    };
  }
}
