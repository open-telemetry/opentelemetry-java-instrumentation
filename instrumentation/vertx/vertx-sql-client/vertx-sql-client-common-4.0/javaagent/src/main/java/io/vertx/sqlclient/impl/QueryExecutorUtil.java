/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.vertx.sqlclient.impl;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.vertx.core.Future;
import io.vertx.sqlclient.PreparedStatement;
import io.vertx.sqlclient.SqlConnectOptions;
import javax.annotation.Nullable;

// Helper class for accessing virtual field on package private QueryExecutor class.
public class QueryExecutorUtil {
  private static final VirtualField<QueryExecutor<?, ?, ?>, SqlConnectOptions> CONNECT_OPTIONS =
      VirtualField.find(QueryExecutor.class, SqlConnectOptions.class);
  private static final VirtualField<QueryExecutor<?, ?, ?>, String> DB_SYSTEM =
      VirtualField.find(QueryExecutor.class, String.class);
  private static final VirtualField<PreparedStatement, SqlConnectOptions>
      PREPARED_STATEMENT_CONNECT_OPTIONS =
          VirtualField.find(PreparedStatement.class, SqlConnectOptions.class);
  private static final VirtualField<PreparedStatement, String> PREPARED_STATEMENT_DB_SYSTEM =
      VirtualField.find(PreparedStatement.class, String.class);

  public static void setConnectOptions(
      Object queryExecutor, @Nullable SqlConnectOptions connectOptions) {
    CONNECT_OPTIONS.set((QueryExecutor<?, ?, ?>) queryExecutor, connectOptions);
  }

  @Nullable
  public static SqlConnectOptions getConnectOptions(Object queryExecutor) {
    return CONNECT_OPTIONS.get((QueryExecutor<?, ?, ?>) queryExecutor);
  }

  public static void setDbSystem(Object queryExecutor, @Nullable String dbSystem) {
    DB_SYSTEM.set((QueryExecutor<?, ?, ?>) queryExecutor, dbSystem);
  }

  @Nullable
  public static String getDbSystem(Object queryExecutor) {
    return DB_SYSTEM.get((QueryExecutor<?, ?, ?>) queryExecutor);
  }

  public static Future<PreparedStatement> attachPreparedStatementData(
      Future<PreparedStatement> future,
      @Nullable SqlConnectOptions connectOptions,
      @Nullable String dbSystem) {
    return future.map(
        preparedStatement -> {
          PREPARED_STATEMENT_CONNECT_OPTIONS.set(preparedStatement, connectOptions);
          PREPARED_STATEMENT_DB_SYSTEM.set(preparedStatement, dbSystem);
          return preparedStatement;
        });
  }

  @Nullable
  public static SqlConnectOptions getPreparedStatementConnectOptions(
      PreparedStatement preparedStatement) {
    return PREPARED_STATEMENT_CONNECT_OPTIONS.get(preparedStatement);
  }

  @Nullable
  public static String getPreparedStatementDbSystem(PreparedStatement preparedStatement) {
    return PREPARED_STATEMENT_DB_SYSTEM.get(preparedStatement);
  }

  private QueryExecutorUtil() {}
}
