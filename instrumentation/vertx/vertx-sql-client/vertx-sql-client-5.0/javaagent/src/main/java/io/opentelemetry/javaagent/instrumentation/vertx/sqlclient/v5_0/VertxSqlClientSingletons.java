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
import io.opentelemetry.instrumentation.api.internal.ScopedThreadValue;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientInfo;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientRequest;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientConnectionPoolState.Submission;
import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.SqlConnectOptions;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.impl.ClientBuilderBase;
import io.vertx.sqlclient.internal.SqlClientBase;
import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Logger;
import javax.annotation.Nullable;

public class VertxSqlClientSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.vertx-sql-client-5.0";
  private static final Instrumenter<VertxSqlClientRequest, Void> instrumenter =
      VertxSqlInstrumenterFactory.createInstrumenter(INSTRUMENTATION_NAME);

  private static final ScopedThreadValue<VertxSqlClientConstructionState> currentConstructionState =
      new ScopedThreadValue<>();
  private static final ScopedThreadValue<Submission> currentSubmission = new ScopedThreadValue<>();
  private static final ScopedThreadValue<ConnectionAttempt> currentConnectionAttempt =
      new ScopedThreadValue<>();
  private static final VirtualField<Pool, VertxSqlClientState> POOL_CLIENT_STATE =
      VirtualField.find(Pool.class, VertxSqlClientState.class);
  private static final VirtualField<SqlClientBase, VertxSqlClientState> CLIENT_STATE =
      VirtualField.find(SqlClientBase.class, VertxSqlClientState.class);
  private static final VirtualField<ClientBuilderBase<?>, List<SqlConnectOptions>>
      BUILDER_DATABASES = VirtualField.find(ClientBuilderBase.class, List.class);

  private static final Logger logger = Logger.getLogger(VertxSqlClientSingletons.class.getName());

  @Nullable
  private static final VirtualField<Object, Context> COMMAND_CONTEXT =
      getVirtualField(
          loadVersionedClass(
              "io.vertx.sqlclient.internal.command.CommandBase",
              "io.vertx.sqlclient.spi.protocol.CommandBase"),
          Context.class);

  @Nullable
  private static final Class<?> CONNECTION_CLASS =
      loadVersionedClass(
          "io.vertx.sqlclient.internal.Connection", "io.vertx.sqlclient.spi.connection.Connection");

  @Nullable
  private static final VirtualField<Object, VertxSqlClientState> CONNECTION_STATE =
      getVirtualField(CONNECTION_CLASS, VertxSqlClientState.class);

  @Nullable private static final Method connectionUnwrapMethod = getUnwrapMethod(CONNECTION_CLASS);

  @Nullable
  private static final Method sqlConnectionUnwrapMethod =
      getUnwrapMethod(loadClass("io.vertx.sqlclient.internal.SqlConnectionInternal"));

  public static Instrumenter<VertxSqlClientRequest, Void> instrumenter() {
    return instrumenter;
  }

  public static ScopedThreadValue<VertxSqlClientConstructionState> currentConstructionState() {
    return currentConstructionState;
  }

  public static ScopedThreadValue<Submission> currentSubmission() {
    return currentSubmission;
  }

  public static ScopedThreadValue<ConnectionAttempt> currentConnectionAttempt() {
    return currentConnectionAttempt;
  }

  public static void setPoolClientState(Pool pool, @Nullable VertxSqlClientState state) {
    POOL_CLIENT_STATE.set(pool, state);
  }

  @Nullable
  public static VertxSqlClientState getPoolClientState(Pool pool) {
    return POOL_CLIENT_STATE.get(pool);
  }

  @Nullable
  @NoMuzzle
  @SuppressWarnings("unchecked") // virtual field key type is not known at compile time
  private static <T> VirtualField<Object, T> getVirtualField(
      @Nullable Class<?> carrierClass, Class<T> fieldClass) {
    return carrierClass != null
        ? (VirtualField<Object, T>) VirtualField.find(carrierClass, fieldClass)
        : null;
  }

  // visible for testing
  @Nullable
  static Class<?> loadVersionedClass(String firstClassName, String secondClassName) {
    Class<?> loadedClass = loadClass(firstClassName);
    return loadedClass != null ? loadedClass : loadClass(secondClassName);
  }

  @Nullable
  private static Class<?> loadClass(String className) {
    try {
      return Class.forName(className, false, VertxSqlClientSingletons.class.getClassLoader());
    } catch (ClassNotFoundException ignored) {
      return null;
    }
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
    VertxSqlClientState state = getClientState(connection);
    if (state == null || state.isSupplier()) {
      return null;
    }
    query.capture(state.getInfo());
    Context executionContext = context.with(QUERY_STATE, null);
    setCommandContext(command, executionContext);
    return executionContext;
  }

  public static void attachClientState(
      SqlClientBase sqlClientBase, @Nullable VertxSqlClientState state) {
    CLIENT_STATE.set(sqlClientBase, state);
  }

  public static Future<SqlConnection> attachClientState(
      Future<SqlConnection> future, @Nullable VertxSqlClientState poolState) {
    return future.transform(
        result -> {
          if (result.succeeded() && result.result() instanceof SqlClientBase) {
            SqlClientBase sqlClientBase = (SqlClientBase) result.result();
            VertxSqlClientState state = poolState;
            if (state == null || state.isSupplier()) {
              VertxSqlClientState connectionState = getClientState(sqlClientBase);
              if (connectionState != null) {
                state = connectionState;
              }
            }
            cacheConnectionState(
                sqlClientBase,
                state != null && state.isSupplier()
                    ? new VertxSqlClientState(state.getInfo(), false)
                    : state);
          }
          return copyResult(result);
        });
  }

  @Nullable
  public static Handler<SqlConnection> wrapConnectHandler(
      @Nullable Handler<SqlConnection> handler, VertxSqlClientState state) {
    if (handler == null) {
      return null;
    }
    return connection -> {
      if (connection instanceof SqlClientBase) {
        cacheConnectionState(connection, state);
      }
      handler.handle(connection);
    };
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
                cacheConnectionState(result.result(), new VertxSqlClientState(info, false));
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

  private static void cacheConnectionState(
      @Nullable Object connection, @Nullable VertxSqlClientState state) {
    // Explicit prepared queries execute on the underlying connection, not the client wrapper.
    Object candidate = connection;
    while (candidate != null) {
      setClientState(candidate, state);
      candidate = unwrap(candidate);
    }
  }

  @Nullable
  public static VertxSqlClientState getClientState(Object client) {
    Object candidate = client;
    while (candidate != null) {
      VertxSqlClientState state = getStoredClientState(candidate);
      if (state != null) {
        if (candidate != client) {
          setClientState(client, state);
        }
        return state;
      }
      candidate = unwrap(candidate);
    }
    return null;
  }

  @Nullable
  private static VertxSqlClientState getStoredClientState(Object client) {
    if (client instanceof SqlClientBase) {
      return CLIENT_STATE.get((SqlClientBase) client);
    }
    return CONNECTION_STATE != null
            && CONNECTION_CLASS != null
            && CONNECTION_CLASS.isInstance(client)
        ? CONNECTION_STATE.get(client)
        : null;
  }

  private static void setClientState(Object client, @Nullable VertxSqlClientState state) {
    if (client instanceof SqlClientBase) {
      CLIENT_STATE.set((SqlClientBase) client, state);
    } else if (CONNECTION_STATE != null
        && CONNECTION_CLASS != null
        && CONNECTION_CLASS.isInstance(client)) {
      CONNECTION_STATE.set(client, state);
    }
  }

  @Nullable
  private static Method getUnwrapMethod(@Nullable Class<?> connectionClass) {
    if (connectionClass == null) {
      return null;
    }
    try {
      return connectionClass.getMethod("unwrap");
    } catch (NoSuchMethodException ignored) {
      return null;
    }
  }

  @Nullable
  private static Object unwrap(Object candidate) {
    Method unwrapMethod = connectionUnwrapMethod;
    if (unwrapMethod == null || !unwrapMethod.getDeclaringClass().isInstance(candidate)) {
      unwrapMethod = sqlConnectionUnwrapMethod;
    }
    if (unwrapMethod == null || !unwrapMethod.getDeclaringClass().isInstance(candidate)) {
      return null;
    }
    try {
      Object unwrapped = unwrapMethod.invoke(candidate);
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

    public void end(@Nullable Throwable throwable) {
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
