/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v4_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientInfoReference;
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

  private static final VirtualField<SqlClientBase<?>, VertxSqlClientInfoReference>
      CLIENT_INFO_REFERENCE =
          VirtualField.find(SqlClientBase.class, VertxSqlClientInfoReference.class);

  public static Instrumenter<VertxSqlClientRequest, Void> instrumenter() {
    return instrumenter;
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

  @Nullable
  public static VertxSqlClientInfoReference getClientInfoReference(SqlClientBase<?> sqlClientBase) {
    return CLIENT_INFO_REFERENCE.get(sqlClientBase);
  }

  private VertxSqlClientSingletons() {}
}
