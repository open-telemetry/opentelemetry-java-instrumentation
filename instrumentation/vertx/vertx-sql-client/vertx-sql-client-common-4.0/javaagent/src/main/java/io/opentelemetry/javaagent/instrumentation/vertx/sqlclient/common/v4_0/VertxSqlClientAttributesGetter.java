/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.SqlDialectUtil.fromDbSystemName;
import static java.util.Collections.singleton;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.SqlResponseStatusCodeUtil;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.function.Function;
import javax.annotation.Nullable;

class VertxSqlClientAttributesGetter
    implements SqlClientAttributesGetter<VertxSqlClientRequest, Void> {

  private static final Function<Throwable, String> sqlStateExtractor = createSqlStateExtractor();
  private static final Function<Throwable, String> mysqlSqlStateExtractor =
      sqlStateExtractor("io.vertx.mysqlclient.MySQLException", "getSqlState");
  private static final Function<Throwable, String> db2SqlStateExtractor =
      sqlStateExtractor("io.vertx.db2client.DB2Exception", "getSqlState");
  private static final Function<Throwable, String> vendorCodeExtractor =
      createNonZeroErrorCodeExtractor();

  @Override
  public String getDbSystemName(VertxSqlClientRequest request) {
    return request.getDbSystemName();
  }

  @Override
  public SqlDialect getSqlDialect(VertxSqlClientRequest request) {
    return fromDbSystemName(request.getDbSystemName());
  }

  @Override
  @Nullable
  public String getDbNamespace(VertxSqlClientRequest request) {
    return request.getDatabase();
  }

  @Nullable
  @Override
  public String getServerAddress(VertxSqlClientRequest request) {
    return request.getConfiguredServerAddress();
  }

  @Nullable
  @Override
  public Integer getServerPort(VertxSqlClientRequest request) {
    return request.getConfiguredServerPort();
  }

  @Override
  public Collection<String> getRawQueryTexts(VertxSqlClientRequest request) {
    return singleton(request.getQueryText());
  }

  @Nullable
  @Override
  public Long getDbOperationBatchSize(VertxSqlClientRequest request) {
    return request.getOperationBatchSize();
  }

  @Nullable
  @Override
  public String getErrorType(
      VertxSqlClientRequest request, @Nullable Void response, @Nullable Throwable error) {
    String sqlState = getAvailableSqlState(error);
    String vendorCode = vendorCodeExtractor.apply(error);
    String responseStatusCode = getResponseStatusCode(request, sqlState, vendorCode);
    if ("00000".equals(sqlState) && vendorCode == null) {
      return null;
    }
    return responseStatusCode;
  }

  @Nullable
  @Override
  public String getDbResponseStatusCode(
      VertxSqlClientRequest request, @Nullable Void response, @Nullable Throwable error) {
    String sqlState = getAvailableSqlState(error);
    String vendorCode = vendorCodeExtractor.apply(error);
    return getResponseStatusCode(request, sqlState, vendorCode);
  }

  @Nullable
  private static String getResponseStatusCode(
      VertxSqlClientRequest request, @Nullable String sqlState, @Nullable String vendorCode) {
    return SqlResponseStatusCodeUtil.getResponseStatusCode(
        request.getDbSystemName(), sqlState, vendorCode);
  }

  @Nullable
  private static String getAvailableSqlState(@Nullable Throwable error) {
    String sqlState = sqlStateExtractor.apply(error);
    if (sqlState == null || sqlState.isEmpty()) {
      sqlState = mysqlSqlStateExtractor.apply(error);
    }
    if (sqlState == null || sqlState.isEmpty()) {
      sqlState = db2SqlStateExtractor.apply(error);
    }
    return sqlState == null || sqlState.isEmpty() ? null : sqlState;
  }

  @Override
  public boolean isParameterizedQuery(VertxSqlClientRequest request, int queryIndex) {
    // Vert.x SQL client does not support mixed parameterization within a single request.
    return request.isParameterizedQuery();
  }

  private static Function<Throwable, String> createSqlStateExtractor() {
    Function<Throwable, String> extractor =
        sqlStateExtractor("io.vertx.sqlclient.DatabaseException", "getSqlState");
    // older versions only have this method
    Function<Throwable, String> fallback =
        sqlStateExtractor("io.vertx.pgclient.PgException", "getCode");
    return error -> {
      String status = extractor.apply(error);
      return status != null ? status : fallback.apply(error);
    };
  }

  private static Function<Throwable, String> createNonZeroErrorCodeExtractor() {
    Function<Throwable, String> databaseExceptionExtractor =
        nonZeroErrorCodeExtractor("io.vertx.sqlclient.DatabaseException");
    Function<Throwable, String> mysqlExceptionExtractor =
        nonZeroErrorCodeExtractor("io.vertx.mysqlclient.MySQLException");
    Function<Throwable, String> db2ExceptionExtractor =
        nonZeroErrorCodeExtractor("io.vertx.db2client.DB2Exception");
    Function<Throwable, String> mssqlExceptionExtractor =
        nonZeroErrorCodeExtractor("io.vertx.mssqlclient.MSSQLException", "number");
    return error -> {
      String errorCode = databaseExceptionExtractor.apply(error);
      if (errorCode == null) {
        errorCode = mysqlExceptionExtractor.apply(error);
      }
      if (errorCode == null) {
        errorCode = db2ExceptionExtractor.apply(error);
      }
      if (errorCode == null) {
        errorCode = mssqlExceptionExtractor.apply(error);
      }
      return errorCode;
    };
  }

  private static Function<Throwable, String> nonZeroErrorCodeExtractor(String className) {
    return nonZeroErrorCodeExtractor(className, "getErrorCode");
  }

  private static Function<Throwable, String> nonZeroErrorCodeExtractor(
      String className, String methodName) {
    try {
      Class<?> exClass = Class.forName(className);
      Method method = exClass.getDeclaredMethod(methodName);

      return error -> {
        if (exClass.isInstance(error)) {
          try {
            Object errorCode = method.invoke(error);
            if (errorCode instanceof Number && ((Number) errorCode).intValue() != 0) {
              return String.valueOf(errorCode);
            }
          } catch (IllegalAccessException | InvocationTargetException ignored) {
            return null;
          }
        }
        return null;
      };
    } catch (ClassNotFoundException | NoSuchMethodException ignored) {
      return error -> null;
    }
  }

  private static Function<Throwable, String> sqlStateExtractor(
      String className, String methodName) {
    try {
      // loaded via reflection, because this class is not available in all versions that we support
      Class<?> exClass = Class.forName(className);
      Method method = exClass.getDeclaredMethod(methodName);

      return (error) -> {
        if (exClass.isInstance(error)) {
          try {
            Object status = method.invoke(error); // can be String or int
            return status == null ? null : String.valueOf(status);
          } catch (IllegalAccessException | InvocationTargetException ignored) {
            return null;
          }
        }
        return null;
      };
    } catch (ClassNotFoundException | NoSuchMethodException ignored) {
      return (error) -> null;
    }
  }
}
