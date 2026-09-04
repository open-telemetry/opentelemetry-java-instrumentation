/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.getDbSystemNameFromClassName;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.getPoolClientInfoProvider;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.setPoolClientInfoProvider;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientInfo;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientInfoCapture;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientInfoProvider;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientRequest;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlInstrumenterFactory;
import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.SqlConnectOptions;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.impl.ClientBuilderBase;
import io.vertx.sqlclient.internal.SqlClientBase;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;

public class VertxSqlClientSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.vertx-sql-client-5.0";
  private static final Instrumenter<VertxSqlClientRequest, Void> instrumenter =
      VertxSqlInstrumenterFactory.createInstrumenter(INSTRUMENTATION_NAME);

  private static final VirtualField<SqlClientBase, VertxSqlClientInfoProvider>
      CLIENT_INFO_PROVIDER =
          VirtualField.find(SqlClientBase.class, VertxSqlClientInfoProvider.class);
  private static final VirtualField<Future<?>, VertxSqlClientInfoCapture> SUPPLIER_FUTURE_CAPTURE =
      VirtualField.find(Future.class, VertxSqlClientInfoCapture.class);
  private static final VirtualField<ClientBuilderBase<?>, List<SqlConnectOptions>>
      BUILDER_DATABASES = VirtualField.find(ClientBuilderBase.class, List.class);

  private static final ThreadLocal<VertxSqlClientInfoCapture> buildingSupplierCapture =
      new ThreadLocal<>();
  private static final ThreadLocal<ConnectionDataListener> pendingConnectionDataListener =
      new ThreadLocal<>();

  @Nullable
  private static final VirtualField<Object, Context> COMMAND_CONTEXT =
      getVersionedVirtualField(
          "io.vertx.sqlclient.internal.command.CommandBase",
          "io.vertx.sqlclient.spi.protocol.CommandBase",
          Context.class);

  @Nullable
  private static final VirtualField<Object, ConnectionDataListener> COMMAND_DATA_LISTENER =
      getVersionedVirtualField(
          "io.vertx.sqlclient.internal.command.CommandBase",
          "io.vertx.sqlclient.spi.protocol.CommandBase",
          ConnectionDataListener.class);

  @Nullable
  private static final VirtualField<Object, VertxSqlClientInfo> CONNECTION_INFO =
      getVersionedVirtualField(
          "io.vertx.sqlclient.internal.Connection",
          "io.vertx.sqlclient.spi.connection.Connection",
          VertxSqlClientInfo.class);

  public static Instrumenter<VertxSqlClientRequest, Void> instrumenter() {
    return instrumenter;
  }

  @Nullable
  @NoMuzzle
  @SuppressWarnings("unchecked") // virtual field key type is not known at compile time
  private static <T> VirtualField<Object, T> getVersionedVirtualField(
      String firstClassName, String secondClassName, Class<T> fieldClass) {
    Class<?> carrierClass = null;
    try {
      carrierClass = Class.forName(firstClassName);
    } catch (ClassNotFoundException ignored) {
      // ignored
    }
    if (carrierClass == null) {
      try {
        carrierClass = Class.forName(secondClassName);
      } catch (ClassNotFoundException ignored) {
        // ignored
      }
    }
    return carrierClass != null
        ? (VirtualField<Object, T>) VirtualField.find(carrierClass, fieldClass)
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
    if (listener != null && COMMAND_DATA_LISTENER != null) {
      COMMAND_DATA_LISTENER.set(command, listener);
    }
  }

  @Nullable
  public static Context notifyConnectionDataListener(Object command, Object connection) {
    if (COMMAND_DATA_LISTENER == null) {
      return null;
    }
    ConnectionDataListener listener = COMMAND_DATA_LISTENER.get(command);
    if (listener == null) {
      return null;
    }
    VertxSqlClientInfo info = getConnectionInfo(connection);
    if (info == null) {
      return null;
    }
    COMMAND_DATA_LISTENER.set(command, null);
    return listener.onConnectionInfo(info);
  }

  @Nullable
  public static VertxSqlClientInfoProvider getClientInfoProvider(SqlClientBase sqlClientBase) {
    return CLIENT_INFO_PROVIDER.get(sqlClientBase);
  }

  public static void attachClientInfoProvider(
      SqlClientBase sqlClientBase, @Nullable VertxSqlClientInfoProvider infoProvider) {
    CLIENT_INFO_PROVIDER.set(sqlClientBase, infoProvider);
  }

  public static Future<SqlConnection> attachClientInfoProvider(
      Future<SqlConnection> future,
      @Nullable VertxSqlClientInfoProvider infoProvider,
      @Nullable Object connectionRequest) {
    return future.transform(
        result -> {
          if (infoProvider instanceof VertxSqlClientInfoCapture && connectionRequest != null) {
            ((VertxSqlClientInfoCapture) infoProvider).removeConnectionRequest(connectionRequest);
          }
          if (result.succeeded() && result.result() instanceof SqlClientBase) {
            SqlClientBase sqlClientBase = (SqlClientBase) result.result();
            VertxSqlClientInfo connectionInfo = getConnectionInfo(sqlClientBase);
            attachClientInfoProvider(
                sqlClientBase, connectionInfo != null ? connectionInfo : infoProvider);
          }
          return copyResult(result);
        });
  }

  @Nullable
  public static Handler<SqlConnection> wrapConnectHandler(
      @Nullable Handler<SqlConnection> handler, VertxSqlClientInfo info) {
    if (handler == null) {
      return null;
    }
    return connection -> {
      if (connection instanceof SqlClientBase) {
        attachClientInfoProvider((SqlClientBase) connection, info);
      }
      handler.handle(connection);
    };
  }

  public static void setPoolSupplierCapture(
      Pool pool, @Nullable VertxSqlClientInfoCapture supplierCapture) {
    setPoolClientInfoProvider(pool, supplierCapture);
  }

  @Nullable
  public static VertxSqlClientInfoCapture getPoolSupplierCapture(Pool pool) {
    VertxSqlClientInfoProvider infoProvider =
        getPoolClientInfoProvider(pool);
    return infoProvider instanceof VertxSqlClientInfoCapture
        ? (VertxSqlClientInfoCapture) infoProvider
        : null;
  }

  public static void setBuildingSupplierCapture(
      @Nullable VertxSqlClientInfoCapture supplierCapture) {
    if (supplierCapture == null) {
      buildingSupplierCapture.remove();
    } else {
      buildingSupplierCapture.set(supplierCapture);
    }
  }

  @Nullable
  public static VertxSqlClientInfoCapture getBuildingSupplierCapture() {
    return buildingSupplierCapture.get();
  }

  public static Supplier<Future<SqlConnectOptions>> wrapConnectOptionsSupplier(
      Supplier<Future<SqlConnectOptions>> supplier,
      VertxSqlClientInfoCapture supplierCapture) {
    return () -> {
      Future<SqlConnectOptions> future = supplier.get();
      if (future != null) {
        Promise<SqlConnectOptions> invocationPromise = Promise.promise();
        future.onComplete(invocationPromise);
        future = invocationPromise.future();
        SUPPLIER_FUTURE_CAPTURE.set(future, supplierCapture);
      }
      return future;
    };
  }

  @Nullable
  public static ConnectionAttempt createConnectionAttempt(
      Object connectionFactory, Future<SqlConnectOptions> connectOptionsFuture) {
    VertxSqlClientInfoCapture supplierCapture =
        SUPPLIER_FUTURE_CAPTURE.get(connectOptionsFuture);
    if (supplierCapture == null) {
      return null;
    }
    SUPPLIER_FUTURE_CAPTURE.set(connectOptionsFuture, null);
    if (supplierCapture.getDbSystemName() == null) {
      supplierCapture.setDbSystemName(getDbSystemNameFromClassName(connectionFactory));
    }
    return new ConnectionAttempt(
        supplierCapture.getDbSystemName(), supplierCapture.takeConnectionRequest());
  }

  public static Future<SqlConnectOptions> captureConnectionAttempt(
      Future<SqlConnectOptions> connectOptionsFuture, ConnectionAttempt connectionAttempt) {
    return connectOptionsFuture.map(
        connectOptions -> {
          connectionAttempt.capture(connectOptions);
          return connectOptions;
        });
  }

  public static <T> Future<T> attachConnectionData(
      Future<T> future, @Nullable ConnectionAttempt connectionAttempt) {
    if (connectionAttempt == null) {
      return future;
    }
    return future.transform(
        result -> {
          VertxSqlClientInfo info = connectionAttempt.info;
          if (info != null) {
            if (result.succeeded()) {
              cacheConnectionInfo(result.result(), info);
            } else {
              connectionAttempt.notifyConnectionDataListener(info);
            }
          }
          return copyResult(result);
        });
  }

  private static <T> Future<T> copyResult(AsyncResult<T> result) {
    return result.succeeded()
        ? Future.succeededFuture(result.result())
        : Future.failedFuture(result.cause());
  }

  private static void cacheConnectionInfo(Object connection, VertxSqlClientInfo info) {
    if (CONNECTION_INFO == null) {
      return;
    }
    Object candidate = connection;
    while (candidate != null) {
      CONNECTION_INFO.set(candidate, info);
      candidate = unwrap(candidate);
    }
  }

  @Nullable
  public static VertxSqlClientInfo getConnectionInfo(Object connection) {
    if (CONNECTION_INFO == null) {
      return null;
    }
    Object candidate = connection;
    while (candidate != null) {
      VertxSqlClientInfo info = CONNECTION_INFO.get(candidate);
      if (info != null) {
        if (candidate != connection) {
          CONNECTION_INFO.set(connection, info);
        }
        return info;
      }
      candidate = unwrap(candidate);
    }
    return null;
  }

  @Nullable
  private static Object unwrap(Object candidate) {
    try {
      Object unwrapped = candidate.getClass().getMethod("unwrap").invoke(candidate);
      return unwrapped != candidate ? unwrapped : null;
    } catch (ReflectiveOperationException ignored) {
      return null;
    }
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
    @Nullable
    Context onConnectionInfo(VertxSqlClientInfo info);
  }

  public static class ConnectionAttempt {
    @Nullable private final String dbSystemName;
    @Nullable private final Object connectionRequest;
    @Nullable private volatile VertxSqlClientInfo info;

    private ConnectionAttempt(@Nullable String dbSystemName, @Nullable Object connectionRequest) {
      this.dbSystemName = dbSystemName;
      this.connectionRequest = connectionRequest;
    }

    private void capture(SqlConnectOptions connectOptions) {
      info =
          VertxSqlClientInfo.create(
              new SqlConnectOptions(connectOptions), dbSystemName);
    }

    private void notifyConnectionDataListener(VertxSqlClientInfo info) {
      if (connectionRequest instanceof ConnectionDataListener) {
        ((ConnectionDataListener) connectionRequest).onConnectionInfo(info);
      }
    }
  }

  private VertxSqlClientSingletons() {}
}
