/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.r2dbc.v1_0.internal;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.SqlDialectUtil.fromDbSystemName;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect;
import io.r2dbc.spi.R2dbcException;
import java.util.Collection;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class R2dbcSqlAttributesGetter
    implements SqlClientAttributesGetter<DbExecution, Void> {

  private static final String MYSQL_DATABASE = "mysql";
  private static final String MARIADB_DATABASE = "mariadb";
  private static final String POSTGRESQL_DATABASE = "postgresql";
  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String ORACLE_DB = "oracle.db";
  private static final String SQL_SERVER_DATABASE = "microsoft.sql_server";
  private static final String DB2_DATABASE = "ibm.db2";

  @Override
  public String getDbSystemName(DbExecution request) {
    return request.getSystemName();
  }

  @Override
  public SqlDialect getSqlDialect(DbExecution request) {
    return fromDbSystemName(request.getSystemName());
  }

  @Override
  @Nullable
  public String getDbNamespace(DbExecution request) {
    return request.getNamespace();
  }

  @Override
  public Collection<String> getRawQueryTexts(DbExecution request) {
    return request.getRawQueryTexts();
  }

  @Override
  @Nullable
  public Long getDbOperationBatchSize(DbExecution request) {
    return request.getBatchSize();
  }

  @Nullable
  @Override
  public String getErrorType(
      DbExecution request, @Nullable Void response, @Nullable Throwable error) {
    String sqlState = getSqlState(error);
    return sqlState != null
        ? sqlState
        : SqlClientAttributesGetter.super.getErrorType(request, null, error);
  }

  @Nullable
  @Override
  public String getDbResponseStatusCode(
      DbExecution request, @Nullable Void response, @Nullable Throwable error) {
    String dbSystemName = getDbSystemName(request);
    int errorCode = getErrorCode(error);
    String sqlState = getAvailableSqlState(error);
    boolean hasErrorCode = errorCode != 0;

    if (prefersVendorCode(dbSystemName) && hasErrorCode) {
      return Integer.toString(errorCode);
    }
    if (POSTGRESQL_DATABASE.equals(dbSystemName) && sqlState != null) {
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
    return MYSQL_DATABASE.equals(dbSystemName)
        || MARIADB_DATABASE.equals(dbSystemName)
        || ORACLE_DB.equals(dbSystemName)
        || SQL_SERVER_DATABASE.equals(dbSystemName)
        || DB2_DATABASE.equals(dbSystemName);
  }

  private static int getErrorCode(@Nullable Throwable error) {
    return error instanceof R2dbcException ? ((R2dbcException) error).getErrorCode() : 0;
  }

  @Nullable
  private static String getSqlState(@Nullable Throwable error) {
    if (error instanceof R2dbcException) {
      String sqlState = ((R2dbcException) error).getSqlState();
      if (sqlState != null) {
        return sqlState;
      }
    }
    return null;
  }

  @Nullable
  private static String getAvailableSqlState(@Nullable Throwable error) {
    String sqlState = getSqlState(error);
    return sqlState == null || sqlState.isEmpty() ? null : sqlState;
  }

  @Nullable
  @Override
  public String getServerAddress(DbExecution request) {
    return request.getConfiguredServerAddress();
  }

  @Nullable
  @Override
  public Integer getServerPort(DbExecution request) {
    return request.getConfiguredServerPort();
  }

  @Override
  public boolean isParameterizedQuery(DbExecution request, int queryIndex) {
    // R2DBC does not support mixed parameterization within a single request.
    return request.isParameterizedQuery();
  }
}
