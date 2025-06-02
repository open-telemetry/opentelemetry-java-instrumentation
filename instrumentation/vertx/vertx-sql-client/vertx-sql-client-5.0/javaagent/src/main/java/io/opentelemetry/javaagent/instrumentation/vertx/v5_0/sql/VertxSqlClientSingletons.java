/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v5_0.sql;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.vertx.sql.VertxSqlClientRequest;
import io.opentelemetry.javaagent.instrumentation.vertx.sql.VertxSqlInstrumenterFactory;
import io.vertx.core.Future;
import io.vertx.sqlclient.SqlConnectOptions;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.internal.SqlClientBase;

public final class VertxSqlClientSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.vertx-sql-client-5.0";
  private static final Instrumenter<VertxSqlClientRequest, Void> INSTRUMENTER =
      VertxSqlInstrumenterFactory.createInstrumenter(INSTRUMENTATION_NAME);

  public static Instrumenter<VertxSqlClientRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private static final VirtualField<SqlClientBase, SqlConnectOptions> connectOptionsField =
      VirtualField.find(SqlClientBase.class, SqlConnectOptions.class);

  public static SqlConnectOptions getSqlConnectOptions(SqlClientBase sqlClientBase) {
    return connectOptionsField.get(sqlClientBase);
  }

  public static void attachConnectOptions(
      SqlClientBase sqlClientBase, SqlConnectOptions connectOptions) {
    connectOptionsField.set(sqlClientBase, connectOptions);
  }

  public static Future<SqlConnection> attachConnectOptions(
      Future<SqlConnection> future, SqlConnectOptions connectOptions) {
    return future.map(
        sqlConnection -> {
          if (sqlConnection instanceof SqlClientBase) {
            connectOptionsField.set((SqlClientBase) sqlConnection, connectOptions);
          }
          return sqlConnection;
        });
  }

  private VertxSqlClientSingletons() {}
}
