/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlAddressGroup;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientDataCapture;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientRequest;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil;
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
import java.util.function.Supplier;
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

  private static final VirtualField<SqlClientBase, VertxSqlAddressGroup> ADDRESS_GROUP =
      VirtualField.find(SqlClientBase.class, VertxSqlAddressGroup.class);

  private static final VirtualField<SqlClientBase, VertxSqlClientDataCapture> DATA_CAPTURE =
      VirtualField.find(SqlClientBase.class, VertxSqlClientDataCapture.class);

  private static final VirtualField<Pool, VertxSqlClientDataCapture> POOL_DATA_CAPTURE =
      VirtualField.find(Pool.class, VertxSqlClientDataCapture.class);

  private static final VirtualField<ClientBuilderBase<?>, List<SqlConnectOptions>>
      BUILDER_DATABASES = VirtualField.find(ClientBuilderBase.class, List.class);

  private static final ThreadLocal<VertxSqlClientDataCapture> buildingDataCapture =
      new ThreadLocal<>();

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

  @Nullable
  public static VertxSqlAddressGroup getAddressGroup(SqlClientBase sqlClientBase) {
    return ADDRESS_GROUP.get(sqlClientBase);
  }

  public static void attachClientState(
      SqlClientBase sqlClientBase,
      @Nullable SqlConnectOptions connectOptions,
      @Nullable VertxSqlAddressGroup addressGroup,
      @Nullable VertxSqlClientDataCapture dataCapture) {
    CONNECT_OPTIONS.set(sqlClientBase, connectOptions);
    ADDRESS_GROUP.set(sqlClientBase, addressGroup);
    DATA_CAPTURE.set(sqlClientBase, dataCapture);
  }

  public static Future<SqlConnection> attachClientState(
      Future<SqlConnection> future,
      @Nullable SqlConnectOptions connectOptions,
      @Nullable VertxSqlAddressGroup addressGroup,
      @Nullable VertxSqlClientDataCapture dataCapture) {
    return future.map(
        sqlConnection -> {
          if (sqlConnection instanceof SqlClientBase) {
            attachClientState(
                (SqlClientBase) sqlConnection, connectOptions, addressGroup, dataCapture);
          }
          return sqlConnection;
        });
  }

  @Nullable
  public static VertxSqlClientDataCapture getDataCapture(SqlClientBase sqlClientBase) {
    return DATA_CAPTURE.get(sqlClientBase);
  }

  public static void setPoolDataCapture(
      Pool pool, @Nullable VertxSqlClientDataCapture dataCapture) {
    if (dataCapture != null) {
      dataCapture.setDbSystem(POOL_DB_SYSTEM.get(pool));
    }
    POOL_DATA_CAPTURE.set(pool, dataCapture);
  }

  @Nullable
  public static VertxSqlClientDataCapture getPoolDataCapture(Pool pool) {
    return POOL_DATA_CAPTURE.get(pool);
  }

  public static void setBuildingDataCapture(@Nullable VertxSqlClientDataCapture dataCapture) {
    if (dataCapture == null) {
      buildingDataCapture.remove();
    } else {
      buildingDataCapture.set(dataCapture);
    }
  }

  @Nullable
  public static VertxSqlClientDataCapture getBuildingDataCapture() {
    return buildingDataCapture.get();
  }

  public static Supplier<Future<SqlConnectOptions>> capture(
      Supplier<Future<SqlConnectOptions>> supplier, VertxSqlClientDataCapture dataCapture) {
    return () -> {
      Future<SqlConnectOptions> future = supplier.get();
      if (future == null) {
        return null;
      }
      return future.map(
          connectOptions -> {
            dataCapture.capture(
                connectOptions,
                connectOptions != null
                    ? VertxSqlClientUtil.getDbSystemNameFromClassName(connectOptions)
                    : null);
            return connectOptions;
          });
    };
  }

  public static void storeBuilderDatabases(
      Object clientBuilder, @Nullable List<SqlConnectOptions> databases) {
    if (clientBuilder instanceof ClientBuilderBase) {
      // The list belongs to the caller and may be mutated or reused.
      BUILDER_DATABASES.set(
          (ClientBuilderBase<?>) clientBuilder,
          databases == null ? null : new ArrayList<>(databases));
    }
  }

  @Nullable
  public static List<SqlConnectOptions> getBuilderDatabases(Object clientBuilder) {
    return clientBuilder instanceof ClientBuilderBase
        ? BUILDER_DATABASES.get((ClientBuilderBase<?>) clientBuilder)
        : null;
  }

  private VertxSqlClientSingletons() {}
}
