/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v3_9.sql;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.vertx.sql.VertxSqlClientRequest;
import io.opentelemetry.javaagent.instrumentation.vertx.sql.VertxSqlInstrumenterFactory;
import io.vertx.sqlclient.SqlConnectOptions;
import io.vertx.sqlclient.impl.SqlClientBase;

public final class VertxSqlClientSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.vertx-sql-client-3.9";

  private static final Instrumenter<VertxSqlClientRequest, Void> INSTRUMENTER =
      VertxSqlInstrumenterFactory.createInstrumenter(INSTRUMENTATION_NAME);

  private static final VirtualField<SqlClientBase<?>, SqlConnectOptions> connectOptionsField =
      VirtualField.find(SqlClientBase.class, SqlConnectOptions.class);

  public static Instrumenter<VertxSqlClientRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  public static SqlConnectOptions getSqlConnectOptions(SqlClientBase<?> sqlClientBase) {
    return connectOptionsField.get(sqlClientBase);
  }

  public static void attachConnectOptions(
      SqlClientBase<?> sqlClientBase, SqlConnectOptions connectOptions) {
    connectOptionsField.set(sqlClientBase, connectOptions);
  }

  private VertxSqlClientSingletons() {}
}
