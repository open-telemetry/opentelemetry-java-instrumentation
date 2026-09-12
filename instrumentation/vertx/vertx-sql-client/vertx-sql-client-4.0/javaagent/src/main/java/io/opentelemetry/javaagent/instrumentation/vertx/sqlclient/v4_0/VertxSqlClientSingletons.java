/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v4_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientInfo;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientRequest;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlInstrumenterFactory;
import io.vertx.core.Future;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PreparedStatement;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.impl.SqlClientBase;
import io.vertx.sqlclient.impl.VertxSqlClientQueryBaseHelper;
import javax.annotation.Nullable;

public class VertxSqlClientSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.vertx-sql-client-4.0";
  private static final Instrumenter<VertxSqlClientRequest, Void> instrumenter =
      VertxSqlInstrumenterFactory.createInstrumenter(INSTRUMENTATION_NAME);

  private static final ThreadLocal<VertxSqlClientInfoReference> clientInfoReference =
      new ThreadLocal<>();
  private static final VirtualField<Pool, VertxSqlClientInfoReference> POOL_CLIENT_INFO_REFERENCE =
      VirtualField.find(Pool.class, VertxSqlClientInfoReference.class);
  private static final VirtualField<PreparedStatement, VertxSqlClientInfoReference>
      PREPARED_STATEMENT_INFO_REFERENCE =
          VirtualField.find(PreparedStatement.class, VertxSqlClientInfoReference.class);

  private static final VirtualField<SqlClientBase<?>, VertxSqlClientInfoReference>
      CLIENT_INFO_REFERENCE =
          VirtualField.find(SqlClientBase.class, VertxSqlClientInfoReference.class);

  public static Instrumenter<VertxSqlClientRequest, Void> instrumenter() {
    return instrumenter;
  }

  public static void setClientInfoReference(@Nullable VertxSqlClientInfoReference value) {
    if (value == null) {
      clientInfoReference.remove();
    } else {
      clientInfoReference.set(value);
    }
  }

  @Nullable
  public static VertxSqlClientInfoReference getClientInfoReference() {
    return clientInfoReference.get();
  }

  @Nullable
  public static VertxSqlClientInfoReference getClientInfoReference(SqlClientBase<?> sqlClientBase) {
    return CLIENT_INFO_REFERENCE.get(sqlClientBase);
  }

  public static void setPoolClientInfoReference(
      Pool pool, @Nullable VertxSqlClientInfoReference value) {
    POOL_CLIENT_INFO_REFERENCE.set(pool, value);
  }

  @Nullable
  public static VertxSqlClientInfoReference getPoolClientInfoReference(Pool pool) {
    return POOL_CLIENT_INFO_REFERENCE.get(pool);
  }

  public static void setQueryExecutorInfoReference(
      Object queryExecutor, @Nullable VertxSqlClientInfoReference infoReference) {
    VertxSqlClientQueryBaseHelper.setData(queryExecutor, infoReference);
  }

  @Nullable
  public static VertxSqlClientInfo getQueryExecutorInfo(Object queryExecutor) {
    VertxSqlClientInfoReference infoReference =
        (VertxSqlClientInfoReference) VertxSqlClientQueryBaseHelper.getData(queryExecutor);
    return infoReference != null ? infoReference.get() : null;
  }

  public static Future<PreparedStatement> attachPreparedStatementInfoReference(
      Future<PreparedStatement> future, VertxSqlClientInfoReference infoReference) {
    return future.map(
        preparedStatement -> {
          PREPARED_STATEMENT_INFO_REFERENCE.set(preparedStatement, infoReference);
          return preparedStatement;
        });
  }

  @Nullable
  public static VertxSqlClientInfoReference getPreparedStatementInfoReference(
      PreparedStatement preparedStatement) {
    return PREPARED_STATEMENT_INFO_REFERENCE.get(preparedStatement);
  }

  public static void attachClientInfoReference(
      SqlClientBase<?> sqlClientBase, @Nullable VertxSqlClientInfoReference infoReference) {
    CLIENT_INFO_REFERENCE.set(sqlClientBase, infoReference);
  }

  public static Future<SqlConnection> attachClientInfoReference(
      Future<SqlConnection> future, @Nullable VertxSqlClientInfoReference infoReference) {
    return future.map(
        sqlConnection -> {
          if (sqlConnection instanceof SqlClientBase) {
            attachClientInfoReference((SqlClientBase<?>) sqlConnection, infoReference);
          }
          return sqlConnection;
        });
  }

  private VertxSqlClientSingletons() {}
}
