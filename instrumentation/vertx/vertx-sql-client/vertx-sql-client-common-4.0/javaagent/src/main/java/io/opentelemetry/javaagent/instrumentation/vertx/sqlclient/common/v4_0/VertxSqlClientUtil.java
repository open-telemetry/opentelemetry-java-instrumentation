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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;

public class VertxSqlClientUtil {

  private static final ThreadLocal<VertxSqlClientInfoProvider> clientInfoProvider =
      new ThreadLocal<>();
  private static final ThreadLocal<SqlConnectOptions> connectOptions = new ThreadLocal<>();
  private static final ThreadLocal<String> dbSystem = new ThreadLocal<>();
  private static final VirtualField<Pool, VertxSqlClientInfoProvider> POOL_CLIENT_INFO =
      VirtualField.find(Pool.class, VertxSqlClientInfoProvider.class);
  private static final VirtualField<Pool, SqlConnectOptions> POOL_CONNECT_OPTIONS =
      VirtualField.find(Pool.class, SqlConnectOptions.class);
  private static final Map<String, String> dbSystemNameByPackage = buildPackageDbSystemNameMap();
  private static final VirtualField<Promise<?>, RequestData> REQUEST_DATA =
      VirtualField.find(Promise.class, RequestData.class);
  private static final VirtualField<PreparedStatement, VertxSqlClientInfo> PREPARED_STATEMENT_INFO =
      VirtualField.find(PreparedStatement.class, VertxSqlClientInfo.class);

  public static void setClientInfoProvider(@Nullable VertxSqlClientInfoProvider value) {
    if (value == null) {
      clientInfoProvider.remove();
    } else {
      clientInfoProvider.set(value);
    }
  }

  @Nullable
  public static VertxSqlClientInfoProvider getClientInfoProvider() {
    return clientInfoProvider.get();
  }

  public static void setSqlConnectOptions(@Nullable SqlConnectOptions value) {
    if (value == null) {
      connectOptions.remove();
    } else {
      connectOptions.set(value);
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

  public static void setPoolClientInfoProvider(
      Pool pool, @Nullable VertxSqlClientInfoProvider value) {
    POOL_CLIENT_INFO.set(pool, value);
  }

  @Nullable
  public static VertxSqlClientInfoProvider getPoolClientInfoProvider(Pool pool) {
    return POOL_CLIENT_INFO.get(pool);
  }

  public static void setPoolConnectOptions(Pool pool, SqlConnectOptions value) {
    POOL_CONNECT_OPTIONS.set(pool, value);
  }

  @Nullable
  public static SqlConnectOptions getPoolSqlConnectOptions(Pool pool) {
    return POOL_CONNECT_OPTIONS.get(pool);
  }

  public static void setQueryExecutorData(
      Object queryExecutor, @Nullable VertxSqlClientInfoProvider infoProvider) {
    QueryExecutorUtil.setData(queryExecutor, infoProvider);
  }

  @Nullable
  public static VertxSqlClientInfoProvider getQueryExecutorInfoProvider(Object queryExecutor) {
    return (VertxSqlClientInfoProvider) QueryExecutorUtil.getData(queryExecutor);
  }

  @Nullable
  public static VertxSqlClientInfo getQueryExecutorInfo(Object queryExecutor) {
    VertxSqlClientInfoProvider infoProvider = getQueryExecutorInfoProvider(queryExecutor);
    return infoProvider != null ? infoProvider.getInfo() : null;
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

  public static String getDbSystemNameFromClassName(@Nullable Object instance) {
    return getDbSystemNameFromClassName(instance != null ? instance.getClass().getName() : null);
  }

  public static String getDbSystemNameFromClassName(@Nullable String className) {
    if (className != null) {
      for (Map.Entry<String, String> entry : dbSystemNameByPackage.entrySet()) {
        if (className.startsWith(entry.getKey())) {
          return entry.getValue();
        }
      }
    }
    return OTHER_SQL;
  }

  public static boolean isKnownDbSystem(String value) {
    return dbSystemNameByPackage.containsValue(value);
  }

  public static String resolveDbSystemName(
      @Nullable SqlConnectOptions connectOptions, @Nullable String declaringTypeName) {
    String dbSystemName = getDbSystemNameFromClassName(connectOptions);
    return isKnownDbSystem(dbSystemName)
        ? dbSystemName
        : getDbSystemNameFromClassName(declaringTypeName);
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
    if (requestData == null || !requestData.tryClaim()) {
      return null;
    }
    REQUEST_DATA.set(promise, null);
    instrumenter.end(requestData.context, requestData.request, null, throwable);
    return requestData.parentContext.makeCurrent();
  }

  private static class RequestData {
    private final AtomicBoolean ended = new AtomicBoolean();
    private final VertxSqlClientRequest request;
    private final Context context;
    private final Context parentContext;

    RequestData(VertxSqlClientRequest request, Context context, Context parentContext) {
      this.request = request;
      this.context = context;
      this.parentContext = parentContext;
    }

    private boolean tryClaim() {
      return ended.compareAndSet(false, true);
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
