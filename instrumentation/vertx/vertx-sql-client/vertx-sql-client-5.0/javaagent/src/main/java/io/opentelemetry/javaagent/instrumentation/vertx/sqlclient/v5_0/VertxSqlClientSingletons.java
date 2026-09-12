/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientQueryState.QUERY_STATE;
import static java.util.logging.Level.FINE;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientInfo;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientRequest;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlInstrumenterFactory;
import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PreparedStatement;
import io.vertx.sqlclient.SqlConnectOptions;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.impl.ClientBuilderBase;
import io.vertx.sqlclient.impl.QueryExecutorUtil;
import io.vertx.sqlclient.internal.SqlClientBase;
import java.util.List;
import java.util.logging.Logger;
import javax.annotation.Nullable;

public class VertxSqlClientSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.vertx-sql-client-5.0";
  private static final Instrumenter<VertxSqlClientRequest, Void> instrumenter =
      VertxSqlInstrumenterFactory.createInstrumenter(INSTRUMENTATION_NAME);

  private static final ThreadLocal<VertxSqlClientInfo> clientInfo = new ThreadLocal<>();
  private static final ThreadLocal<VertxSqlClientSupplierInfo> querySupplier = new ThreadLocal<>();
  private static final ThreadLocal<VertxSqlClientConstructionState> constructionState =
      new ThreadLocal<>();
  private static final VirtualField<PreparedStatement, VertxSqlClientInfo> PREPARED_STATEMENT_INFO =
      VirtualField.find(PreparedStatement.class, VertxSqlClientInfo.class);
  private static final VirtualField<Pool, VertxSqlClientInfo> POOL_CLIENT_INFO =
      VirtualField.find(Pool.class, VertxSqlClientInfo.class);
  private static final VirtualField<SqlClientBase, VertxSqlClientInfo> CLIENT_INFO =
      VirtualField.find(SqlClientBase.class, VertxSqlClientInfo.class);
  private static final VirtualField<SqlClientBase, VertxSqlClientSupplierInfo> CLIENT_SUPPLIER =
      VirtualField.find(SqlClientBase.class, VertxSqlClientSupplierInfo.class);
  private static final VirtualField<ClientBuilderBase<?>, List<SqlConnectOptions>>
      BUILDER_DATABASES = VirtualField.find(ClientBuilderBase.class, List.class);

  private static final Logger logger = Logger.getLogger(VertxSqlClientSingletons.class.getName());

  @Nullable
  private static final VirtualField<Object, Context> COMMAND_CONTEXT =
      getVersionedVirtualField(
          "io.vertx.sqlclient.internal.command.CommandBase",
          "io.vertx.sqlclient.spi.protocol.CommandBase",
          Context.class);

  @Nullable
  private static final VirtualField<Object, VertxSqlClientInfo> CONNECTION_INFO =
      getVersionedVirtualField(
          "io.vertx.sqlclient.internal.Connection",
          "io.vertx.sqlclient.spi.connection.Connection",
          VertxSqlClientInfo.class);

  public static Instrumenter<VertxSqlClientRequest, Void> instrumenter() {
    return instrumenter;
  }

  public static void setClientInfo(@Nullable VertxSqlClientInfo value) {
    if (value == null) {
      clientInfo.remove();
    } else {
      clientInfo.set(value);
    }
  }

  @Nullable
  public static VertxSqlClientInfo getClientInfo() {
    return clientInfo.get();
  }

  @Nullable
  public static VertxSqlClientInfo getClientInfo(SqlClientBase sqlClientBase) {
    return CLIENT_INFO.get(sqlClientBase);
  }

  public static void captureQueryExecutorInfo(Object queryExecutor) {
    VertxSqlClientSupplierInfo supplier = querySupplier.get();
    QueryExecutorUtil.setData(queryExecutor, supplier != null ? supplier : getClientInfo());
  }

  @Nullable
  public static VertxSqlClientInfo getQueryExecutorInfo(Object queryExecutor) {
    Object data = QueryExecutorUtil.getData(queryExecutor);
    return data instanceof VertxSqlClientSupplierInfo
        ? ((VertxSqlClientSupplierInfo) data).getInfo()
        : (VertxSqlClientInfo) data;
  }

  public static boolean isSupplierQuery(Object queryExecutor) {
    return QueryExecutorUtil.getData(queryExecutor) instanceof VertxSqlClientSupplierInfo;
  }

  public static void setQuerySupplier(@Nullable VertxSqlClientSupplierInfo supplier) {
    if (supplier == null) {
      querySupplier.remove();
    } else {
      querySupplier.set(supplier);
    }
  }

  @Nullable
  public static VertxSqlClientSupplierInfo getClientSupplier(SqlClientBase client) {
    return CLIENT_SUPPLIER.get(client);
  }

  public static void setClientSupplier(
      SqlClientBase client, @Nullable VertxSqlClientSupplierInfo supplier) {
    CLIENT_SUPPLIER.set(client, supplier);
  }

  public static void setPoolClientInfo(Pool pool, @Nullable VertxSqlClientInfo info) {
    POOL_CLIENT_INFO.set(pool, info);
  }

  @Nullable
  public static VertxSqlClientInfo getPoolClientInfo(Pool pool) {
    return POOL_CLIENT_INFO.get(pool);
  }

  public static Future<PreparedStatement> attachPreparedStatementInfo(
      Future<PreparedStatement> future, VertxSqlClientInfo info) {
    return future.map(
        preparedStatement -> {
          PREPARED_STATEMENT_INFO.set(preparedStatement, info);
          return preparedStatement;
        });
  }

  @Nullable
  public static VertxSqlClientInfo getPreparedStatementInfo(PreparedStatement preparedStatement) {
    return PREPARED_STATEMENT_INFO.get(preparedStatement);
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

  @Nullable
  public static Context captureConnectionInfo(Object command, Object connection) {
    Context context = getCommandContext(command);
    if (context == null) {
      return null;
    }
    VertxSqlClientQueryState query = context.get(QUERY_STATE);
    if (query == null) {
      return null;
    }
    VertxSqlClientInfo info = getConnectionInfo(connection);
    if (info == null) {
      return null;
    }
    query.capture(info);
    Context executionContext = context.with(QUERY_STATE, null);
    setCommandContext(command, executionContext);
    return executionContext;
  }

  public static void attachClientInfo(
      SqlClientBase sqlClientBase, @Nullable VertxSqlClientInfo info) {
    CLIENT_INFO.set(sqlClientBase, info);
    CLIENT_SUPPLIER.set(sqlClientBase, null);
  }

  public static Future<SqlConnection> attachClientInfo(
      Future<SqlConnection> future, @Nullable VertxSqlClientInfo info) {
    return future.transform(
        result -> {
          if (result.succeeded() && result.result() instanceof SqlClientBase) {
            SqlClientBase sqlClientBase = (SqlClientBase) result.result();
            VertxSqlClientInfo connectionInfo = getConnectionInfo(sqlClientBase);
            attachClientInfo(sqlClientBase, connectionInfo != null ? connectionInfo : info);
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
        attachClientInfo((SqlClientBase) connection, info);
      }
      handler.handle(connection);
    };
  }

  public static void setConstructionState(@Nullable VertxSqlClientConstructionState state) {
    if (state == null) {
      constructionState.remove();
    } else {
      constructionState.set(state);
    }
  }

  @Nullable
  public static VertxSqlClientConstructionState getConstructionState() {
    return constructionState.get();
  }

  public static Future<SqlConnectOptions> captureConnectionAttempt(
      Future<SqlConnectOptions> connectOptionsFuture, ConnectionAttempt connectionAttempt) {
    return connectOptionsFuture.map(
        connectOptions -> {
          try {
            connectionAttempt.capture(connectOptions);
          } catch (Throwable t) {
            logger.log(FINE, "Failed to capture Vert.x SQL connection options", t);
          }
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
          try {
            VertxSqlClientInfo info = connectionAttempt.info;
            if (info != null) {
              if (result.succeeded()) {
                cacheConnectionInfo(result.result(), info);
              } else {
                connectionAttempt.captureFailureInfo(info);
              }
            }
          } catch (Throwable t) {
            logger.log(FINE, "Failed to attach Vert.x SQL connection metadata", t);
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

  public static void setBuilderDatabases(
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

  public static class ConnectionAttempt {
    private final String dbSystemName;
    @Nullable private final VertxSqlClientQueryState query;
    @Nullable private volatile VertxSqlClientInfo info;
    @Nullable private Scope scope;

    ConnectionAttempt(String dbSystemName, @Nullable VertxSqlClientQueryState query) {
      this.dbSystemName = dbSystemName;
      this.query = query;
      this.scope = query != null ? query.getContext().makeCurrent() : null;
    }

    private void capture(SqlConnectOptions connectOptions) {
      info = VertxSqlClientInfo.create(connectOptions, dbSystemName);
    }

    private void captureFailureInfo(VertxSqlClientInfo info) {
      if (query != null) {
        query.capture(info);
      }
    }

    void end(@Nullable Throwable throwable) {
      if (scope != null) {
        scope.close();
        scope = null;
      }
      if (throwable != null && query != null) {
        query.end(throwable);
      }
    }
  }

  private VertxSqlClientSingletons() {}
}
