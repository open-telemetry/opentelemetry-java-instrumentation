/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlAddressGroup;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientRequest;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlInstrumenterFactory;
import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;
import io.vertx.core.Future;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.SqlConnectOptions;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.impl.ClientBuilderBase;
import io.vertx.sqlclient.internal.SqlClientBase;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

public class VertxSqlClientSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.vertx-sql-client-5.0";
  private static final Instrumenter<VertxSqlClientRequest, Void> instrumenter =
      VertxSqlInstrumenterFactory.createInstrumenter(INSTRUMENTATION_NAME);

  private static final VirtualField<Pool, String> poolDbSystem =
      VirtualField.find(Pool.class, String.class);

  private static final VirtualField<SqlConnectOptions, String> connectOptionsDbSystem =
      VirtualField.find(SqlConnectOptions.class, String.class);

  private static final VirtualField<SqlClientBase, SqlConnectOptions> connectOptionsField =
      VirtualField.find(SqlClientBase.class, SqlConnectOptions.class);

  private static final VirtualField<SqlClientBase, VertxSqlAddressGroup> addressGroupField =
      VirtualField.find(SqlClientBase.class, VertxSqlAddressGroup.class);

  private static final VirtualField<ClientBuilderBase<?>, List<SqlConnectOptions>>
      builderDatabases = VirtualField.find(ClientBuilderBase.class, List.class);

  @Nullable
  private static final VirtualField<Object, Context> commandContextField =
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
    return commandContextField != null ? commandContextField.get(command) : null;
  }

  public static void setCommandContext(Object command, Context context) {
    if (commandContextField != null) {
      commandContextField.set(command, context);
    }
  }

  public static void storePoolDbSystem(Pool pool, String dbSystem) {
    poolDbSystem.set(pool, dbSystem);
  }

  @Nullable
  public static String getConnectOptionsDbSystem(SqlConnectOptions sqlConnectOptions) {
    return connectOptionsDbSystem.get(sqlConnectOptions);
  }

  public static void resolveAndStoreDbSystem(Pool pool, SqlConnectOptions sqlConnectOptions) {
    String dbSystem = poolDbSystem.get(pool);
    if (sqlConnectOptions != null && dbSystem != null) {
      connectOptionsDbSystem.set(sqlConnectOptions, dbSystem);
    }
  }

  @Nullable
  public static SqlConnectOptions getSqlConnectOptions(SqlClientBase sqlClientBase) {
    return connectOptionsField.get(sqlClientBase);
  }

  @Nullable
  public static VertxSqlAddressGroup getAddressGroup(SqlClientBase sqlClientBase) {
    return addressGroupField.get(sqlClientBase);
  }

  public static void attachClientState(
      SqlClientBase sqlClientBase,
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
            attachClientState((SqlClientBase) sqlConnection, connectOptions, addressGroup);
          }
          return sqlConnection;
        });
  }

  /** Keeps a snapshot, so that a later change to the caller's list does not change the client. */
  public static void storeBuilderDatabases(
      Object clientBuilder, @Nullable List<SqlConnectOptions> databases) {
    if (clientBuilder instanceof ClientBuilderBase) {
      builderDatabases.set(
          (ClientBuilderBase<?>) clientBuilder,
          databases == null ? null : new ArrayList<>(databases));
    }
  }

  @Nullable
  public static List<SqlConnectOptions> getBuilderDatabases(Object clientBuilder) {
    return clientBuilder instanceof ClientBuilderBase
        ? builderDatabases.get((ClientBuilderBase<?>) clientBuilder)
        : null;
  }

  private VertxSqlClientSingletons() {}
}
