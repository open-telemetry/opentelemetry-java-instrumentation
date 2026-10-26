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
  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String ORACLE_DB = "oracle.db";
  private static final String SQL_SERVER_DATABASE = "microsoft.sql_server";
  private static final String DB2_DATABASE = "ibm.db2";
  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String OTHER_SQL_DATABASE = "other_sql";

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
    return getSqlState(error);
  }

  @Nullable
  @Override
  public String getDbResponseStatusCode(
      DbExecution request, @Nullable Void response, @Nullable Throwable error) {
    String dbSystemName = getDbSystemName(request);
    if (MYSQL_DATABASE.equals(dbSystemName)
        || MARIADB_DATABASE.equals(dbSystemName)
        || ORACLE_DB.equals(dbSystemName)
        || SQL_SERVER_DATABASE.equals(dbSystemName)
        || DB2_DATABASE.equals(dbSystemName)) {
      int errorCode = getErrorCode(error);
      if (errorCode != 0) {
        return Integer.toString(errorCode);
      }
    }
    String sqlState = getSqlState(error);
    int errorCode = getErrorCode(error);
    if (OTHER_SQL_DATABASE.equals(dbSystemName)
        && sqlState != null
        && !sqlState.isEmpty()
        && errorCode != 0) {
      return sqlState + "/" + errorCode;
    }
    if (sqlState != null) {
      return sqlState;
    }
    return errorCode == 0 ? null : Integer.toString(errorCode);
  }

  private static int getErrorCode(@Nullable Throwable error) {
    return error instanceof R2dbcException ? ((R2dbcException) error).getErrorCode() : 0;
  }

  @Nullable
  private static String getSqlState(@Nullable Throwable error) {
    if (error instanceof R2dbcException) {
      return ((R2dbcException) error).getSqlState();
    }
    return null;
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
