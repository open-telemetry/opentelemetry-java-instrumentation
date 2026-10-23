/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.SqlDialectUtil.fromDbSystemName;
import static io.opentelemetry.semconv.DbAttributes.DbSystemNameValues.MICROSOFT_SQL_SERVER;
import static io.opentelemetry.semconv.DbAttributes.DbSystemNameValues.MYSQL;
import static io.opentelemetry.semconv.DbAttributes.DbSystemNameValues.POSTGRESQL;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.IBM_DB2;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.ORACLE_DB;
import static java.util.Collections.singleton;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.function.Function;
import javax.annotation.Nullable;

class VertxSqlClientAttributesGetter
    implements SqlClientAttributesGetter<VertxSqlClientRequest, Void> {

  private static final Function<Throwable, String> responseStatusExtractor =
      createResponseStatusExtractor(false);
  private static final Function<Throwable, String> dbResponseStatusExtractor =
      createResponseStatusExtractor(true);
  private static final Function<Throwable, String> mysqlDbResponseStatusExtractor =
      responseStatusExtractor("io.vertx.mysqlclient.MySQLException", "getSqlState", true);
  private static final Function<Throwable, String> db2DbResponseStatusExtractor =
      responseStatusExtractor("io.vertx.db2client.DB2Exception", "getSqlState", true);
  private static final Function<Throwable, String> dbResponseErrorCodeExtractor =
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
    String errorType = responseStatusExtractor.apply(error);
    return errorType != null ? errorType : error == null ? null : error.getClass().getName();
  }

  @Nullable
  @Override
  public String getDbResponseStatusCode(
      VertxSqlClientRequest request, @Nullable Void response, @Nullable Throwable error) {
    String dbSystemName = request.getDbSystemName();
    String sqlState = getAvailableSqlState(error);
    String errorCode = dbResponseErrorCodeExtractor.apply(error);

    if (prefersVendorCode(dbSystemName) && errorCode != null) {
      return errorCode;
    }
    if (POSTGRESQL.equals(dbSystemName) && sqlState != null) {
      return sqlState;
    }
    if (sqlState != null && errorCode != null) {
      return sqlState + "/" + errorCode;
    }
    return sqlState != null ? sqlState : errorCode;
  }

  private static boolean prefersVendorCode(String dbSystemName) {
    return MYSQL.equals(dbSystemName)
        || ORACLE_DB.equals(dbSystemName)
        || MICROSOFT_SQL_SERVER.equals(dbSystemName)
        || IBM_DB2.equals(dbSystemName);
  }

  @Nullable
  private static String getAvailableSqlState(@Nullable Throwable error) {
    String sqlState = dbResponseStatusExtractor.apply(error);
    if (sqlState == null || sqlState.isEmpty()) {
      sqlState = mysqlDbResponseStatusExtractor.apply(error);
    }
    if (sqlState == null || sqlState.isEmpty()) {
      sqlState = db2DbResponseStatusExtractor.apply(error);
    }
    return sqlState == null || sqlState.isEmpty() ? null : sqlState;
  }

  @Override
  public boolean isParameterizedQuery(VertxSqlClientRequest request, int queryIndex) {
    // Vert.x SQL client does not support mixed parameterization within a single request.
    return request.isParameterizedQuery();
  }

  private static Function<Throwable, String> createResponseStatusExtractor(boolean preserveNull) {
    Function<Throwable, String> extractor =
        responseStatusExtractor(
            "io.vertx.sqlclient.DatabaseException", "getSqlState", preserveNull);
    // older versions only have this method
    Function<Throwable, String> fallback =
        responseStatusExtractor("io.vertx.pgclient.PgException", "getCode", preserveNull);
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

  private static Function<Throwable, String> responseStatusExtractor(
      String className, String methodName, boolean preserveNull) {
    try {
      // loaded via reflection, because this class is not available in all versions that we support
      Class<?> exClass = Class.forName(className);
      Method method = exClass.getDeclaredMethod(methodName);

      return (error) -> {
        if (exClass.isInstance(error)) {
          try {
            Object status = method.invoke(error); // can be String or int
            return status == null && preserveNull ? null : String.valueOf(status);
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
