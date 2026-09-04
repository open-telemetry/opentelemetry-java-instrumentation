/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v4_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientData;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientRequest;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlInstrumenterFactory;
import io.vertx.core.Future;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.impl.SqlClientBase;
import javax.annotation.Nullable;

public class VertxSqlClientSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.vertx-sql-client-4.0";
  private static final Instrumenter<VertxSqlClientRequest, Void> instrumenter =
      VertxSqlInstrumenterFactory.createInstrumenter(INSTRUMENTATION_NAME);

  private static final VirtualField<SqlClientBase<?>, VertxSqlClientData> CLIENT_DATA =
      VirtualField.find(SqlClientBase.class, VertxSqlClientData.class);

  public static Instrumenter<VertxSqlClientRequest, Void> instrumenter() {
    return instrumenter;
  }

  public static VertxSqlClientData getClientData(SqlClientBase<?> sqlClientBase) {
    return CLIENT_DATA.get(sqlClientBase);
  }

  public static void attachClientState(
      SqlClientBase<?> sqlClientBase, @Nullable VertxSqlClientData data) {
    CLIENT_DATA.set(sqlClientBase, data);
  }

  public static Future<SqlConnection> attachClientState(
      Future<SqlConnection> future, @Nullable VertxSqlClientData data) {
    return future.map(
        sqlConnection -> {
          if (sqlConnection instanceof SqlClientBase) {
            attachClientState((SqlClientBase<?>) sqlConnection, data);
          }
          return sqlConnection;
        });
  }

  private VertxSqlClientSingletons() {}
}
