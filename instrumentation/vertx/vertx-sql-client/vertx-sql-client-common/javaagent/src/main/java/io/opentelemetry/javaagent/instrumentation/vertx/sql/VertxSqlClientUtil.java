/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sql;

import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.DB2;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.MICROSOFT_SQL_SERVER;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.MYSQL;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.ORACLE_DB;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.OTHER_SQL;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.POSTGRESQL;

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

  private static final Map<String, String> DB_SYSTEM_BY_PACKAGE = buildPackageDbSystemMap();

  public static void setPoolConnectOptions(Pool pool, SqlConnectOptions sqlConnectOptions) {
    poolConnectOptions.set(pool, sqlConnectOptions);
  }

  public static SqlConnectOptions getPoolSqlConnectOptions(Pool pool) {
    return poolConnectOptions.get(pool);
  }

  public static String getDbSystemFromClassName(@Nullable Object instance) {
    if (instance != null) {
      String className = instance.getClass().getName();
      for (Map.Entry<String, String> entry : DB_SYSTEM_BY_PACKAGE.entrySet()) {
        if (className.startsWith(entry.getKey())) {
          return entry.getValue();
        }
      }
    }
    return OTHER_SQL;
  }

  // See https://github.com/eclipse-vertx/vertx-sql-client for the full list of supported
  // database-specific client modules
  private static Map<String, String> buildPackageDbSystemMap() {
    Map<String, String> map = new HashMap<>();
    map.put("io.vertx.pgclient.", POSTGRESQL);
    map.put("io.vertx.mysqlclient.", MYSQL);
    map.put("io.vertx.mssqlclient.", MICROSOFT_SQL_SERVER);
    map.put("io.vertx.oracleclient.", ORACLE_DB);
    map.put("io.vertx.db2client.", DB2);
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
