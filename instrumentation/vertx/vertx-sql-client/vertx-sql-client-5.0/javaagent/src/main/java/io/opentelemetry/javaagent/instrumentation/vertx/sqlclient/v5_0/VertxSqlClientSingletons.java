/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlAddressGroup;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientData;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientDataCapture;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientDataProvider;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientRequest;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlInstrumenterFactory;
import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.SqlConnectOptions;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.impl.ClientBuilderBase;
import io.vertx.sqlclient.internal.SqlClientBase;
import java.util.List;
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

  private static final VirtualField<SqlClientBase, VertxSqlClientDataProvider> DATA_PROVIDER =
      VirtualField.find(SqlClientBase.class, VertxSqlClientDataProvider.class);

  private static final Cache<Object, VertxSqlClientData> connectionDataCache = Cache.weak();
  private static final Cache<Object, ConnectionDataListener> commandDataListenerCache =
      Cache.weak();

  private static final VirtualField<Pool, VertxSqlClientDataCapture> POOL_DATA_CAPTURE =
      VirtualField.find(Pool.class, VertxSqlClientDataCapture.class);

  private static final VirtualField<ClientBuilderBase<?>, List<SqlConnectOptions>>
      BUILDER_DATABASES = VirtualField.find(ClientBuilderBase.class, List.class);

  private static final ThreadLocal<VertxSqlClientDataCapture> buildingDataCapture =
      new ThreadLocal<>();
  private static final ThreadLocal<ConnectionDataListener> pendingConnectionDataListener =
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

  public static void setPendingConnectionDataListener(@Nullable ConnectionDataListener listener) {
    if (listener == null) {
      pendingConnectionDataListener.remove();
    } else {
      pendingConnectionDataListener.set(listener);
    }
  }

  public static void capturePendingConnectionDataListener(Object command) {
    ConnectionDataListener listener = pendingConnectionDataListener.get();
    if (listener != null) {
      commandDataListenerCache.put(command, listener);
    }
  }

  @Nullable
  public static Context notifyConnectionDataListener(Object command, Object connection) {
    ConnectionDataListener listener = commandDataListenerCache.get(command);
    if (listener == null) {
      return null;
    }
    VertxSqlClientData data = getConnectionData(connection);
    if (data == null) {
      return null;
    }
    commandDataListenerCache.remove(command);
    return listener.onConnectionData(data);
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
      @Nullable VertxSqlClientDataProvider dataProvider) {
    CONNECT_OPTIONS.set(sqlClientBase, connectOptions);
    ADDRESS_GROUP.set(sqlClientBase, addressGroup);
    DATA_PROVIDER.set(sqlClientBase, dataProvider);
  }

  public static Future<SqlConnection> attachClientState(
      Future<SqlConnection> future,
      @Nullable SqlConnectOptions connectOptions,
      @Nullable VertxSqlAddressGroup addressGroup,
      @Nullable VertxSqlClientDataCapture dataCapture) {
    return future.map(
        sqlConnection -> {
          if (sqlConnection instanceof SqlClientBase) {
            SqlClientBase sqlClientBase = (SqlClientBase) sqlConnection;
            VertxSqlClientData data = dataCapture != null ? getConnectionData(sqlClientBase) : null;
            if (data != null) {
              attachClientState(
                  sqlClientBase, data.getConnectOptions(), data.getAddressGroup(), data);
            } else {
              attachClientState(sqlClientBase, connectOptions, addressGroup, null);
            }
          }
          return sqlConnection;
        });
  }

  @Nullable
  public static Handler<SqlConnection> wrapConnectHandler(
      @Nullable Handler<SqlConnection> handler,
      SqlConnectOptions connectOptions,
      VertxSqlAddressGroup addressGroup) {
    if (handler == null) {
      return null;
    }
    return connection -> {
      if (connection instanceof SqlClientBase) {
        attachClientState((SqlClientBase) connection, connectOptions, addressGroup, null);
      }
      handler.handle(connection);
    };
  }

  public static <T> Future<T> attachConnectionData(
      Future<T> future, SqlConnectOptions connectOptions, String dbSystem) {
    VertxSqlClientData data = VertxSqlClientData.fromConnectOptions(connectOptions, dbSystem);
    return future.map(
        connection -> {
          connectionDataCache.put(connection, data);
          return connection;
        });
  }

  @Nullable
  public static VertxSqlClientData getConnectionData(Object connection) {
    Object candidate = connection;
    while (candidate != null) {
      VertxSqlClientData data = connectionDataCache.get(candidate);
      if (data != null) {
        return data;
      }
      try {
        Object unwrapped = candidate.getClass().getMethod("unwrap").invoke(candidate);
        if (unwrapped == candidate) {
          return null;
        }
        candidate = unwrapped;
      } catch (ReflectiveOperationException ignored) {
        return null;
      }
    }
    return null;
  }

  @Nullable
  public static VertxSqlClientDataProvider getDataProvider(SqlClientBase sqlClientBase) {
    return DATA_PROVIDER.get(sqlClientBase);
  }

  public static void setPoolDataCapture(
      Pool pool, @Nullable VertxSqlClientDataCapture dataCapture) {
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

  public static void storeBuilderDatabases(
      Object clientBuilder, @Nullable List<SqlConnectOptions> databases) {
    if (clientBuilder instanceof ClientBuilderBase) {
      BUILDER_DATABASES.set((ClientBuilderBase<?>) clientBuilder, databases);
    }
  }

  @Nullable
  public static List<SqlConnectOptions> getBuilderDatabases(Object clientBuilder) {
    return clientBuilder instanceof ClientBuilderBase
        ? BUILDER_DATABASES.get((ClientBuilderBase<?>) clientBuilder)
        : null;
  }

  public interface ConnectionDataListener {

    /**
     * Returns the context of the span this listener started, or {@code null} if it started none.
     */
    @Nullable
    Context onConnectionData(VertxSqlClientData data);
  }

  private VertxSqlClientSingletons() {}
}
