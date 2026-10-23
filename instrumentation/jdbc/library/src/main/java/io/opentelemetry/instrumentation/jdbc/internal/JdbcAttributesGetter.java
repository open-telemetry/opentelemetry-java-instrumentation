/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.SqlDialectUtil.fromDbSystemName;
import static io.opentelemetry.semconv.DbAttributes.DbSystemNameValues.MARIADB;
import static io.opentelemetry.semconv.DbAttributes.DbSystemNameValues.MICROSOFT_SQL_SERVER;
import static io.opentelemetry.semconv.DbAttributes.DbSystemNameValues.MYSQL;
import static io.opentelemetry.semconv.DbAttributes.DbSystemNameValues.POSTGRESQL;

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
  private static final String IBM_DB2 = "ibm.db2";
  private static final String ORACLE_DB = "oracle.db";
  private static final String SQLITE = "sqlite";

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
    String errorCode = getSqlExceptionCode(error);
    return errorCode != null
        ? errorCode
        : error == null ? null : error.getClass().getName();
  }

  @Nullable
  @Override
  public String getDbResponseStatusCode(
      DbRequest request, @Nullable Void response, @Nullable Throwable error) {
    if (!(error instanceof SQLException)) {
      return null;
    }
    SQLException sqlException = (SQLException) error;
    String dbSystemName = getDbSystemName(request);
    String sqlState = getAvailableSqlState(sqlException);
    int errorCode = sqlException.getErrorCode();
    boolean hasErrorCode = errorCode != 0;

    if (prefersVendorCode(dbSystemName) && hasErrorCode) {
      return Integer.toString(errorCode);
    }
    if (POSTGRESQL.equals(dbSystemName) && sqlState != null) {
      return sqlState;
    }
    if (sqlState != null && hasErrorCode) {
      return sqlState + "/" + errorCode;
    }
    if (sqlState != null) {
      return sqlState;
    }
    return hasErrorCode ? Integer.toString(errorCode) : null;
  }

  private static boolean prefersVendorCode(String dbSystemName) {
    return IBM_DB2.equals(dbSystemName)
        || MARIADB.equals(dbSystemName)
        || MICROSOFT_SQL_SERVER.equals(dbSystemName)
        || MYSQL.equals(dbSystemName)
        || ORACLE_DB.equals(dbSystemName)
        || SQLITE.equals(dbSystemName);
  }

  @Nullable
  private static String getAvailableSqlState(SQLException sqlException) {
    String sqlState = sqlException.getSQLState();
    return sqlState == null || sqlState.isEmpty() ? null : sqlState;
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
