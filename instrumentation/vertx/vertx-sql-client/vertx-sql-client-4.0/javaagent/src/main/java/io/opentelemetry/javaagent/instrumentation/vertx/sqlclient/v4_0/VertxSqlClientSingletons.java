/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v4_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientInfoProvider;
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

  private static final VirtualField<SqlClientBase<?>, VertxSqlClientInfoProvider> CLIENT_INFO =
      VirtualField.find(SqlClientBase.class, VertxSqlClientInfoProvider.class);

  public static Instrumenter<VertxSqlClientRequest, Void> instrumenter() {
    return instrumenter;
  }

  public static void attachClientInfoProvider(
      SqlClientBase<?> sqlClientBase, @Nullable VertxSqlClientInfoProvider infoProvider) {
    CLIENT_INFO.set(sqlClientBase, infoProvider);
  }

  public static Future<SqlConnection> attachClientInfoProvider(
      Future<SqlConnection> future, @Nullable VertxSqlClientInfoProvider infoProvider) {
    return future.map(
        sqlConnection -> {
          if (sqlConnection instanceof SqlClientBase) {
            attachClientInfoProvider((SqlClientBase<?>) sqlConnection, infoProvider);
          }
          return sqlConnection;
        });
  }

  @Nullable
  public static VertxSqlClientInfoProvider getClientInfoProvider(SqlClientBase<?> sqlClientBase) {
    return CLIENT_INFO.get(sqlClientBase);
  }

  private VertxSqlClientSingletons() {}
}
