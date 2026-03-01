/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sql;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.SqlConnectOptions;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;

public final class VertxSqlClientUtil {
  private static final ThreadLocal<SqlConnectOptions> connectOptions = new ThreadLocal<>();

  public static void setSqlConnectOptions(SqlConnectOptions sqlConnectOptions) {
    connectOptions.set(sqlConnectOptions);
  }

  public static SqlConnectOptions getSqlConnectOptions() {
    return connectOptions.get();
  }

  private static final VirtualField<Pool, SqlConnectOptions> poolConnectOptions =
      VirtualField.find(Pool.class, SqlConnectOptions.class);

  private static final VirtualField<SqlConnectOptions, String> connectOptionsDbSystem =
      VirtualField.find(SqlConnectOptions.class, String.class);

  private static final VirtualField<Pool, String> poolDbSystem =
      VirtualField.find(Pool.class, String.class);

  private static final Map<String, String> DB_SYSTEM_BY_DRIVER_CLASS = buildDriverDbSystemMap();

  // copied from VertxSqlClientRequest
  private static final String POSTGRESQL = "postgresql";
  private static final String MYSQL = "mysql";
  private static final String MICROSOFT_SQL_SERVER = "microsoft.sql_server";
  private static final String ORACLE_DB = "oracle.db";
  private static final String DB2 = "db2";

  private static final Map<String, String> DB_SYSTEM_BY_POOL_CLASS = buildPoolDbSystemMap();

  public static void setPoolConnectOptions(Pool pool, SqlConnectOptions sqlConnectOptions) {
    poolConnectOptions.set(pool, sqlConnectOptions);
  }

  public static SqlConnectOptions getPoolSqlConnectOptions(Pool pool) {
    return poolConnectOptions.get(pool);
  }

  @Nullable
  public static String getConnectOptionsDbSystem(SqlConnectOptions sqlConnectOptions) {
    if (sqlConnectOptions == null) {
      return null;
    }
    return connectOptionsDbSystem.get(sqlConnectOptions);
  }

  public static void storePoolDbSystem(Pool pool, String dbSystem) {
    poolDbSystem.set(pool, dbSystem);
  }

  @Nullable
  public static String getDbSystemFromDriverClass(String driverClassName) {
    return DB_SYSTEM_BY_DRIVER_CLASS.get(driverClassName);
  }

  /**
   * Resolve the database system name from the Pool implementation class hierarchy and store it on
   * the SqlConnectOptions for later retrieval.
   */
  public static void resolveAndStoreDbSystem(Pool pool, SqlConnectOptions sqlConnectOptions) {
    if (sqlConnectOptions == null) {
      return;
    }
    String dbSystem = resolveDbSystemFromPool(pool);
    if (dbSystem == null) {
      // vertx 5.0: pool class is generic PoolImpl; db system was stored by DriverInstrumentation
      dbSystem = poolDbSystem.get(pool);
    }
    if (dbSystem != null) {
      connectOptionsDbSystem.set(sqlConnectOptions, dbSystem);
    }
  }

  @Nullable
  private static String resolveDbSystemFromPool(Pool pool) {
    Class<?> clazz = pool.getClass();
    while (clazz != null) {
      String dbSystem = DB_SYSTEM_BY_POOL_CLASS.get(clazz.getName());
      if (dbSystem != null) {
        return dbSystem;
      }
      for (Class<?> iface : clazz.getInterfaces()) {
        dbSystem = DB_SYSTEM_BY_POOL_CLASS.get(iface.getName());
        if (dbSystem != null) {
          return dbSystem;
        }
      }
      clazz = clazz.getSuperclass();
    }
    return null;
  }

  private static Map<String, String> buildPoolDbSystemMap() {
    Map<String, String> map = new HashMap<>();
    map.put("io.vertx.pgclient.PgPool", POSTGRESQL);
    map.put("io.vertx.mysqlclient.MySQLPool", MYSQL);
    map.put("io.vertx.mssqlclient.MSSQLPool", MICROSOFT_SQL_SERVER);
    map.put("io.vertx.oracleclient.OraclePool", ORACLE_DB);
    map.put("io.vertx.db2client.DB2Pool", DB2);
    return map;
  }

  private static Map<String, String> buildDriverDbSystemMap() {
    Map<String, String> map = new HashMap<>();
    map.put("io.vertx.pgclient.spi.PgDriver", POSTGRESQL);
    map.put("io.vertx.mysqlclient.spi.MySQLDriver", MYSQL);
    map.put("io.vertx.mssqlclient.spi.MSSQLDriver", MICROSOFT_SQL_SERVER);
    map.put("io.vertx.oracleclient.spi.OracleDriver", ORACLE_DB);
    map.put("io.vertx.db2client.spi.DB2Driver", DB2);
    return map;
  }

  private static final VirtualField<Promise<?>, RequestData> requestDataField =
      VirtualField.find(Promise.class, RequestData.class);

  public static void attachRequest(
      Promise<?> promise, VertxSqlClientRequest request, Context context, Context parentContext) {
    requestDataField.set(promise, new RequestData(request, context, parentContext));
  }

  public static Scope endQuerySpan(
      Instrumenter<VertxSqlClientRequest, Void> instrumenter,
      Promise<?> promise,
      Throwable throwable) {
    RequestData requestData = requestDataField.get(promise);
    if (requestData == null) {
      return null;
    }
    instrumenter.end(requestData.context, requestData.request, null, throwable);
    return requestData.parentContext.makeCurrent();
  }

  static class RequestData {
    final VertxSqlClientRequest request;
    final Context context;
    final Context parentContext;

    RequestData(VertxSqlClientRequest request, Context context, Context parentContext) {
      this.request = request;
      this.context = context;
      this.parentContext = parentContext;
    }
  }

  public static <T> Future<T> wrapContext(Future<T> future) {
    Context context = Context.current();
    CompletableFuture<T> result = new CompletableFuture<>();
    future
        .toCompletionStage()
        .whenComplete(
            (value, throwable) -> {
              try (Scope ignore = context.makeCurrent()) {
                if (throwable != null) {
                  result.completeExceptionally(throwable);
                } else {
                  result.complete(value);
                }
              }
            });
    return Future.fromCompletionStage(result);
  }

  private VertxSqlClientUtil() {}
}
