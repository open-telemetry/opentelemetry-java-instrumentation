/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientRequest;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlInstrumenterFactory;
import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;
import io.vertx.core.Future;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.SqlConnectOptions;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.internal.SqlClientBase;
import javax.annotation.Nullable;

public class VertxSqlClientSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.vertx-sql-client-5.0";
  private static final Instrumenter<VertxSqlClientRequest, Void> instrumenter =
      VertxSqlInstrumenterFactory.createInstrumenter(INSTRUMENTATION_NAME);

  private static final VirtualField<Pool, String> POOL_DB_SYSTEM =
      VirtualField.find(Pool.class, String.class);

  private static final VirtualField<SqlConnectOptions, String> CONNECT_OPTIONS_DB_SYSTEM =
      VirtualField.find(SqlConnectOptions.class, String.class);

  private static final VirtualField<SqlClientBase, SqlConnectOptions> CONNECT_OPTIONS =
      VirtualField.find(SqlClientBase.class, SqlConnectOptions.class);

  @Nullable
  private static final VirtualField<Object, Context> COMMAND_CONTEXT =
      getCommandContextVirtualField();

  public static Instrumenter<VertxSqlClientRequest, Void> instrumenter() {
    return instrumenter;
  }

  @NoMuzzle // to skip virtual field detection in this method
  @SuppressWarnings("unchecked") // virtual field key type is not known at compile time
  private static VirtualField<Object, Context> getCommandContextVirtualField() {
    // CommandBase that we want to attach context to is in different packages in 5.0 and 5.1
    Class<?> commandClass = null;
    try {
      // 5.0.0
      commandClass = Class.forName("io.vertx.sqlclient.internal.command.CommandBase");
    } catch (ClassNotFoundException ignored) {
      // ignored
    }
    if (commandClass == null) {
      try {
        // 5.1.0
        commandClass = Class.forName("io.vertx.sqlclient.spi.protocol.CommandBase");
      } catch (ClassNotFoundException ignored) {
        // ignored
      }
    }
    return commandClass != null
        ? (VirtualField<Object, Context>) VirtualField.find(commandClass, Context.class)
        : null;
  }

  @Nullable
  public static Context getCommandContext(Object command) {
    return COMMAND_CONTEXT != null ? COMMAND_CONTEXT.get(command) : null;
  }

  public static void setCommandContext(Object command, Context context) {
    if (COMMAND_CONTEXT != null) {
      COMMAND_CONTEXT.set(command, context);
    }
  }

  public static void storePoolDbSystem(Pool pool, String dbSystem) {
    POOL_DB_SYSTEM.set(pool, dbSystem);
  }

  @Nullable
  public static String getConnectOptionsDbSystem(SqlConnectOptions sqlConnectOptions) {
    return CONNECT_OPTIONS_DB_SYSTEM.get(sqlConnectOptions);
  }

  public static void resolveAndStoreDbSystem(Pool pool, SqlConnectOptions sqlConnectOptions) {
    String dbSystem = POOL_DB_SYSTEM.get(pool);
    if (sqlConnectOptions != null && dbSystem != null) {
      CONNECT_OPTIONS_DB_SYSTEM.set(sqlConnectOptions, dbSystem);
    }
  }

  @Nullable
  public static SqlConnectOptions getSqlConnectOptions(SqlClientBase sqlClientBase) {
    return CONNECT_OPTIONS.get(sqlClientBase);
  }

  public static void attachConnectOptions(
      SqlClientBase sqlClientBase, @Nullable SqlConnectOptions connectOptions) {
    CONNECT_OPTIONS.set(sqlClientBase, connectOptions);
  }

  public static Future<SqlConnection> attachConnectOptions(
      Future<SqlConnection> future, @Nullable SqlConnectOptions connectOptions) {
    return future.map(
        sqlConnection -> {
          if (sqlConnection instanceof SqlClientBase) {
            CONNECT_OPTIONS.set((SqlClientBase) sqlConnection, connectOptions);
          }
          return sqlConnection;
        });
  }

  private VertxSqlClientSingletons() {}
}
