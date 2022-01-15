/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import static io.opentelemetry.instrumentation.jdbc.internal.JdbcUtils.connectionFromStatement;
import static io.opentelemetry.instrumentation.jdbc.internal.JdbcUtils.extractDbInfo;

import com.google.auto.value.AutoValue;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import javax.annotation.Nullable;

@AutoValue
public abstract class DbRequest {

  @Nullable
  public static DbRequest create(PreparedStatement statement) {
    return create(statement, JdbcData.preparedStatement.get(statement));
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

  public abstract DbInfo getDbInfo();

  @Nullable
  public abstract String getStatement();
}
