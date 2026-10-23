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
import static java.util.logging.Level.FINE;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.SqlConnectOptions;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import javax.annotation.Nullable;

public class VertxSqlClientUtil {

  private static final Logger logger = Logger.getLogger(VertxSqlClientUtil.class.getName());

  private static final Map<String, String> dbSystemNameByPackage = buildPackageDbSystemNameMap();
  private static final VirtualField<Promise<?>, RequestData> REQUEST_DATA =
      VirtualField.find(Promise.class, RequestData.class);

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
    Context parentContext = endQuerySpanAndGetParentContext(instrumenter, promise, throwable);
    return parentContext != null ? parentContext.makeCurrent() : null;
  }

  @Nullable
  public static Context endQuerySpanAndGetParentContext(
      Instrumenter<VertxSqlClientRequest, Void> instrumenter,
      Promise<?> promise,
      @Nullable Throwable throwable) {
    RequestData requestData = REQUEST_DATA.get(promise);
    if (requestData == null || !requestData.ended.compareAndSet(false, true)) {
      return null;
    }
    REQUEST_DATA.set(promise, null);
    if (requestData.request instanceof VertxSqlClientDeferredRequest
        && ((VertxSqlClientDeferredRequest) requestData.request).freezeInfo()) {
      try {
        VertxSqlInstrumenterFactory.updateSpanName(requestData.context, requestData.request);
      } catch (Throwable t) {
        logger.log(FINE, "Failed to update Vert.x SQL span name", t);
      }
    }
    instrumenter.end(requestData.context, requestData.request, null, throwable);
    return requestData.parentContext;
  }

  private static class RequestData {
    private final VertxSqlClientRequest request;
    private final Context context;
    private final Context parentContext;
    private final AtomicBoolean ended = new AtomicBoolean();

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
