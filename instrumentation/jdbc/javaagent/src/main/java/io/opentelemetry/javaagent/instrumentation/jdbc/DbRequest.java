/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc;

import static io.opentelemetry.javaagent.instrumentation.jdbc.JdbcUtils.connectionFromStatement;
import static io.opentelemetry.javaagent.instrumentation.jdbc.JdbcUtils.extractDbInfo;

import com.google.auto.value.AutoValue;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import org.checkerframework.checker.nullness.qual.Nullable;

@AutoValue
public abstract class DbRequest {
  public abstract DbInfo getDbInfo();

  @Nullable
  public abstract String getStatement();

  @Nullable
  public static DbRequest create(PreparedStatement statement) {
    return create(statement, JdbcMaps.preparedStatements.get(statement));
  }

  @Nullable
  public static DbRequest create(Statement statement, String dbStatementString) {
    Connection connection = connectionFromStatement(statement);
    if (connection == null) {
      return null;
    }

    return create(extractDbInfo(connection), dbStatementString);
  }

  public static DbRequest create(DbInfo dbInfo, String statement) {
    return new AutoValue_DbRequest(dbInfo, statement);
  }
}
