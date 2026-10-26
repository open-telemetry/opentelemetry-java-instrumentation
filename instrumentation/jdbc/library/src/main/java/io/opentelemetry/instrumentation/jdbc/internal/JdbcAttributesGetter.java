/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.SqlDialectUtil.fromDbSystemName;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class JdbcAttributesGetter implements SqlClientAttributesGetter<DbRequest, Void> {

  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String ORACLE_DB = "oracle.db";
  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String OTHER_SQL = "other_sql";
  private static final String H2_DATABASE = "h2database";
  private static final int H2_OBJECT_CLOSED_ERROR_CODE = 90007;
  private static final String POSTGRESQL_DATABASE = "postgresql";
  private static final String POSTGRESQL_CONNECTION_DOES_NOT_EXIST = "08003";
  private static final String POSTGRESQL_CONNECTION_CLASS = "org.postgresql.jdbc.PgConnection";

  @Override
  public String getDbSystemName(DbRequest request) {
    return request.getDbInfo().getDbSystemName();
  }

  @Nullable
  @Override
  public String getDbNamespace(DbRequest request) {
    return request.getDbInfo().getDbNamespace();
  }

  @Override
  public SqlDialect getSqlDialect(DbRequest request) {
    return fromDbSystemName(request.getDbInfo().getDbSystemName());
  }

  @Override
  public Collection<String> getRawQueryTexts(DbRequest request) {
    return request.getQueryTexts();
  }

  @Override
  public Long getDbOperationBatchSize(DbRequest request) {
    return request.getBatchSize();
  }

  @Nullable
  @Override
  public String getErrorType(
      DbRequest request, @Nullable Void response, @Nullable Throwable error) {
    return getSqlExceptionCode(error);
  }

  @Nullable
  @Override
  public String getDbResponseStatusCode(
      DbRequest request, @Nullable Void response, @Nullable Throwable error) {
    if (H2_DATABASE.equals(getDbSystemName(request))
        && error instanceof SQLException
        && ((SQLException) error).getErrorCode() == H2_OBJECT_CLOSED_ERROR_CODE
        && "90007".equals(((SQLException) error).getSQLState())) {
      // H2 generates this object-closed error locally before sending a database request.
      return null;
    }
    if (ORACLE_DB.equals(getDbSystemName(request)) && error instanceof SQLException) {
      int errorCode = ((SQLException) error).getErrorCode();
      // Oracle Statement.ensureOpen() generates these locally, before any server request.
      if (errorCode == 17008 || errorCode == 17009) {
        return null;
      }
    }
    if (POSTGRESQL_DATABASE.equals(getDbSystemName(request))
        && error instanceof SQLException
        && isPostgresqlConnectionClosed((SQLException) error)) {
      return null;
    }
    if (OTHER_SQL.equals(getDbSystemName(request)) && error instanceof SQLException) {
      SQLException sqlException = (SQLException) error;
      int errorCode = sqlException.getErrorCode();
      String sqlState = sqlException.getSQLState();
      boolean hasSqlState = sqlState != null && !sqlState.isEmpty() && !sqlState.equals("00000");
      if (hasSqlState && errorCode != 0) {
        return sqlState + "/" + errorCode;
      }
      if (hasSqlState) {
        return sqlState;
      }
      if (errorCode != 0) {
        return Integer.toString(errorCode);
      }
      return null;
    }
    return getSqlExceptionCode(error);
  }

  private static boolean isPostgresqlConnectionClosed(SQLException error) {
    if (!POSTGRESQL_CONNECTION_DOES_NOT_EXIST.equals(error.getSQLState())) {
      return false;
    }
    StackTraceElement[] stackTrace = error.getStackTrace();
    return stackTrace.length > 0
        && POSTGRESQL_CONNECTION_CLASS.equals(stackTrace[0].getClassName())
        && "checkClosed".equals(stackTrace[0].getMethodName());
  }

  @Nullable
  private static String getSqlExceptionCode(@Nullable Throwable error) {
    if (!(error instanceof SQLException)) {
      return null;
    }
    SQLException sqlException = (SQLException) error;
    int errorCode = sqlException.getErrorCode();
    if (errorCode != 0) {
      return Integer.toString(errorCode);
    }
    String sqlState = sqlException.getSQLState();
    if (sqlState == null || sqlState.isEmpty() || sqlState.equals("00000")) {
      return null;
    }
    return sqlState;
  }

  @Override
  public Map<String, String> getDbQueryParameters(DbRequest request) {
    return request.getPreparedStatementParameters();
  }

  @Override
  public boolean isParameterizedQuery(DbRequest request, int queryIndex) {
    // JDBC does not support mixed parameterization within a single request.
    return request.isParameterizedQuery();
  }

  @Nullable
  @Override
  public String getServerAddress(DbRequest request) {
    DbInfo dbInfo = request.getDbInfo();
    DbServerTarget target = dbInfo.getConfiguredServerTarget();
    return target == null ? null : target.getAddress();
  }

  @Nullable
  @Override
  public Integer getServerPort(DbRequest request) {
    DbInfo dbInfo = request.getDbInfo();
    DbServerTarget target = dbInfo.getConfiguredServerTarget();
    return target == null ? null : target.getPort();
  }
}
