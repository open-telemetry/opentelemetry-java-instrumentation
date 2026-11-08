/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.SqlDialectUtil.fromDbSystemName;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.SqlResponseStatusCodeUtil;
import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.Collection;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class JdbcAttributesGetter implements SqlClientAttributesGetter<DbRequest, Void> {

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
    if (error instanceof SQLTimeoutException) {
      return error.getClass().getName();
    }
    if (!(error instanceof SQLException)) {
      return null;
    }
    String responseStatusCode = getDbResponseStatusCode(request, (SQLException) error);
    return "00000".equals(responseStatusCode) ? null : responseStatusCode;
  }

  @Nullable
  @Override
  public String getDbResponseStatusCode(
      DbRequest request, @Nullable Void response, @Nullable Throwable error) {
    if (!(error instanceof SQLException)) {
      return null;
    }
    return getDbResponseStatusCode(request, (SQLException) error);
  }

  @Nullable
  private String getDbResponseStatusCode(DbRequest request, SQLException sqlException) {
    String dbSystemName = getDbSystemName(request);
    String sqlState = sqlException.getSQLState();
    int errorCode = sqlException.getErrorCode();
    String vendorCode = errorCode == 0 ? null : Integer.toString(errorCode);
    return SqlResponseStatusCodeUtil.getResponseStatusCode(dbSystemName, sqlState, vendorCode);
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
