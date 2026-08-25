/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v4_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlAddressGroup;
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

  private static final VirtualField<SqlClientBase<?>, VertxSqlAddressGroup> addressGroupField =
      VirtualField.find(SqlClientBase.class, VertxSqlAddressGroup.class);

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

  @Nullable
  public static VertxSqlAddressGroup getAddressGroup(SqlClientBase<?> sqlClientBase) {
    return addressGroupField.get(sqlClientBase);
  }

  public static void attachClientState(
      SqlClientBase<?> sqlClientBase,
      @Nullable SqlConnectOptions connectOptions,
      @Nullable VertxSqlAddressGroup addressGroup) {
    connectOptionsField.set(sqlClientBase, connectOptions);
    addressGroupField.set(sqlClientBase, addressGroup);
  }

  public static Future<SqlConnection> attachClientState(
      Future<SqlConnection> future,
      @Nullable SqlConnectOptions connectOptions,
      @Nullable VertxSqlAddressGroup addressGroup) {
    return future.map(
        sqlConnection -> {
          if (sqlConnection instanceof SqlClientBase) {
            attachClientState((SqlClientBase<?>) sqlConnection, connectOptions, addressGroup);
          }
          return sqlConnection;
        });
  }

  private VertxSqlClientSingletons() {}
}
