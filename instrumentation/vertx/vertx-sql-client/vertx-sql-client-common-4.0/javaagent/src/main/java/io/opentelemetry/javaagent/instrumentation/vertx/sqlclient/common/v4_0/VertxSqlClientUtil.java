/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import static io.opentelemetry.semconv.DbAttributes.DbSystemNameValues.MICROSOFT_SQL_SERVER;
import static io.opentelemetry.semconv.DbAttributes.DbSystemNameValues.MYSQL;
import static io.opentelemetry.semconv.DbAttributes.DbSystemNameValues.POSTGRESQL;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.IBM_DB2;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.ORACLE_DB;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.OTHER_SQL;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PreparedStatement;
import io.vertx.sqlclient.SqlConnectOptions;
import io.vertx.sqlclient.impl.QueryExecutorUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;

public class VertxSqlClientUtil {

  private static final ThreadLocal<SqlConnectOptions> connectOptions = new ThreadLocal<>();
  private static final ThreadLocal<String> dbSystem = new ThreadLocal<>();
  private static final ThreadLocal<VertxSqlAddressGroup> addressGroup = new ThreadLocal<>();
  private static final VirtualField<Pool, SqlConnectOptions> POOL_CONNECT_OPTIONS =
      VirtualField.find(Pool.class, SqlConnectOptions.class);
  private static final VirtualField<Pool, VertxSqlAddressGroup> POOL_ADDRESS_GROUP =
      VirtualField.find(Pool.class, VertxSqlAddressGroup.class);
  private static final Map<String, String> dbSystemNameByPackage = buildPackageDbSystemNameMap();
  private static final VirtualField<Promise<?>, RequestData> REQUEST_DATA =
      VirtualField.find(Promise.class, RequestData.class);
  private static final VirtualField<PreparedStatement, VertxSqlClientData> PREPARED_STATEMENT_DATA =
      VirtualField.find(PreparedStatement.class, VertxSqlClientData.class);

  /** The server that stands for a client configured with a list of them. */
  @Nullable
  public static SqlConnectOptions firstDatabase(
      @Nullable List<? extends SqlConnectOptions> databases) {
    return databases == null || databases.isEmpty() ? null : databases.get(0);
  }

  public static void setSqlConnectOptions(@Nullable SqlConnectOptions sqlConnectOptions) {
    if (sqlConnectOptions == null) {
      connectOptions.remove();
    } else {
      connectOptions.set(sqlConnectOptions);
    }
  }

  @Nullable
  public static SqlConnectOptions getSqlConnectOptions() {
    return connectOptions.get();
  }

  public static void setDbSystem(@Nullable String value) {
    if (value == null) {
      dbSystem.remove();
    } else {
      dbSystem.set(value);
    }
  }

  @Nullable
  public static String getDbSystem() {
    return dbSystem.get();
  }

  public static void setAddressGroup(@Nullable VertxSqlAddressGroup value) {
    if (value == null) {
      addressGroup.remove();
    } else {
      addressGroup.set(value);
    }
  }

  @Nullable
  public static VertxSqlAddressGroup getAddressGroup() {
    return addressGroup.get();
  }

  public static void setPoolConnectOptions(Pool pool, SqlConnectOptions sqlConnectOptions) {
    POOL_CONNECT_OPTIONS.set(pool, sqlConnectOptions);
  }

  @Nullable
  public static SqlConnectOptions getPoolSqlConnectOptions(Pool pool) {
    return POOL_CONNECT_OPTIONS.get(pool);
  }

  public static void setPoolAddressGroup(Pool pool, @Nullable VertxSqlAddressGroup value) {
    POOL_ADDRESS_GROUP.set(pool, value);
  }

  @Nullable
  public static VertxSqlAddressGroup getPoolAddressGroup(Pool pool) {
    return POOL_ADDRESS_GROUP.get(pool);
  }

  public static void setQueryExecutorData(Object queryExecutor, VertxSqlClientData data) {
    QueryExecutorUtil.setData(queryExecutor, data);
  }

  @Nullable
  public static VertxSqlClientData getQueryExecutorData(Object queryExecutor) {
    return (VertxSqlClientData) QueryExecutorUtil.getData(queryExecutor);
  }

  public static Future<PreparedStatement> attachPreparedStatementData(
      Future<PreparedStatement> future, VertxSqlClientData data) {
    return future.map(
        preparedStatement -> {
          PREPARED_STATEMENT_DATA.set(preparedStatement, data);
          return preparedStatement;
        });
  }

  @Nullable
  public static VertxSqlClientData getPreparedStatementData(PreparedStatement preparedStatement) {
    return PREPARED_STATEMENT_DATA.get(preparedStatement);
  }

  public static String getDbSystemNameFromClassName(@Nullable Object instance) {
    if (instance != null) {
      String className = instance.getClass().getName();
      for (Map.Entry<String, String> entry : dbSystemNameByPackage.entrySet()) {
        if (className.startsWith(entry.getKey())) {
          return entry.getValue();
        }
      }
    }
    return OTHER_SQL;
  }

  // See https://github.com/eclipse-vertx/vertx-sql-client for the full list of supported
  // database-specific client modules
  private static Map<String, String> buildPackageDbSystemNameMap() {
    Map<String, String> map = new HashMap<>();
    map.put("io.vertx.pgclient.", POSTGRESQL);
    map.put("io.vertx.mysqlclient.", MYSQL);
    map.put("io.vertx.mssqlclient.", MICROSOFT_SQL_SERVER);
    map.put("io.vertx.oracleclient.", ORACLE_DB);
    map.put("io.vertx.db2client.", IBM_DB2);
    return map;
  }

  public static void attachRequest(
      Promise<?> promise, VertxSqlClientRequest request, Context context, Context parentContext) {
    REQUEST_DATA.set(promise, new RequestData(request, context, parentContext));
  }

  @Nullable
  public static Scope endQuerySpan(
      Instrumenter<VertxSqlClientRequest, Void> instrumenter,
      Promise<?> promise,
      @Nullable Throwable throwable) {
    RequestData requestData = REQUEST_DATA.get(promise);
    if (requestData == null) {
      return null;
    }
    instrumenter.end(requestData.context, requestData.request, null, throwable);
    return requestData.parentContext.makeCurrent();
  }

  private static class RequestData {
    private final VertxSqlClientRequest request;
    private final Context context;
    private final Context parentContext;

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
