/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.vertx.sqlclient.impl;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientData;
import io.vertx.core.Future;
import io.vertx.sqlclient.PreparedStatement;
import javax.annotation.Nullable;

// Helper class for accessing virtual field on package private QueryExecutor class.
public class QueryExecutorUtil {
  private static final VirtualField<QueryExecutor<?, ?, ?>, VertxSqlClientData> DATA =
      VirtualField.find(QueryExecutor.class, VertxSqlClientData.class);
  private static final VirtualField<PreparedStatement, VertxSqlClientData> PREPARED_STATEMENT_DATA =
      VirtualField.find(PreparedStatement.class, VertxSqlClientData.class);

  public static void setData(Object queryExecutor, @Nullable VertxSqlClientData data) {
    DATA.set((QueryExecutor<?, ?, ?>) queryExecutor, data);
  }

  @Nullable
  public static VertxSqlClientData getData(Object queryExecutor) {
    return DATA.get((QueryExecutor<?, ?, ?>) queryExecutor);
  }

  public static Future<PreparedStatement> attachPreparedStatementData(
      Future<PreparedStatement> future, @Nullable VertxSqlClientData data) {
    return future.map(
        preparedStatement -> {
          PREPARED_STATEMENT_DATA.set(preparedStatement, data);
          return preparedStatement;
        });
  }

  @Nullable
  public static VertxSqlClientData getPreparedStatementData(PreparedStatement preparedStatement) {
    return PREPARED_STATEMENT_DATA.get(preparedStatement);
  }

  public static void copyQueryExecutorData(Object sourceQuery, Object copiedQuery) {
    QueryExecutor<?, ?, ?> sourceExecutor = ((QueryBase<?, ?>) sourceQuery).builder;
    QueryExecutor<?, ?, ?> copiedExecutor = ((QueryBase<?, ?>) copiedQuery).builder;
    DATA.set(copiedExecutor, DATA.get(sourceExecutor));
  }

  private QueryExecutorUtil() {}
}
