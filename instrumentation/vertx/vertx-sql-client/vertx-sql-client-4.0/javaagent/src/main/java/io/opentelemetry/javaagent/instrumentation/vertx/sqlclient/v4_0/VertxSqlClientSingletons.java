/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v4_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientRequest;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlInstrumenterFactory;
import io.vertx.core.Future;
import io.vertx.sqlclient.SqlConnectOptions;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.impl.SqlClientBase;
import javax.annotation.Nullable;

public class VertxSqlClientSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.vertx-sql-client-4.0";
  private static final Instrumenter<VertxSqlClientRequest, Void> instrumenter =
      VertxSqlInstrumenterFactory.createInstrumenter(INSTRUMENTATION_NAME);

  private static final VirtualField<SqlClientBase<?>, SqlConnectOptions> connectOptionsField =
      VirtualField.find(SqlClientBase.class, SqlConnectOptions.class);

  private static final VirtualField<SqlConnectOptions, String> connectOptionsDbSystem =
      VirtualField.find(SqlConnectOptions.class, String.class);

  public static Instrumenter<VertxSqlClientRequest, Void> instrumenter() {
    return instrumenter;
  }

  public static void storeConnectOptionsDbSystem(
      SqlConnectOptions connectOptions, String dbSystem) {
    connectOptionsDbSystem.set(connectOptions, dbSystem);
  }

  @Nullable
  public static String getConnectOptionsDbSystem(SqlConnectOptions connectOptions) {
    // null when db system was not captured at pool creation time; callers should fall back
    // to getDbSystemNameFromClassName() on the connect options instance
    return connectOptionsDbSystem.get(connectOptions);
  }

  @Nullable
  public static SqlConnectOptions getSqlConnectOptions(SqlClientBase<?> sqlClientBase) {
    return connectOptionsField.get(sqlClientBase);
  }

  public static void attachConnectOptions(
      SqlClientBase<?> sqlClientBase, @Nullable SqlConnectOptions connectOptions) {
    connectOptionsField.set(sqlClientBase, connectOptions);
  }

  public static Future<SqlConnection> attachConnectOptions(
      Future<SqlConnection> future, @Nullable SqlConnectOptions connectOptions) {
    return future.map(
        sqlConnection -> {
          if (sqlConnection instanceof SqlClientBase) {
            connectOptionsField.set((SqlClientBase<?>) sqlConnection, connectOptions);
          }
          return sqlConnection;
        });
  }

  private VertxSqlClientSingletons() {}
}
