/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.SqlDialectUtil.fromDbSystemName;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect;
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

  @Override
  public String getDbSystemName(DbRequest request) {
    return request.getDbInfo().getDbSystemName();
  }

  @Deprecated // to be removed in 3.0
  @Override
  public String getDbSystem(DbRequest request) {
    return request.getDbInfo().getDbSystem();
  }

  @Deprecated // to be removed in 3.0
  @Nullable
  @Override
  public String getUser(DbRequest request) {
    return request.getDbInfo().getDbUser();
  }

  @Nullable
  @Override
  public String getDbNamespace(DbRequest request) {
    return request.getDbInfo().getDbNamespace();
  }

  @Deprecated // to be removed in 3.0
  @Nullable
  @Override
  public String getDbName(DbRequest request) {
    return request.getDbInfo().getDbName();
  }

  @Deprecated // to be removed in 3.0
  @Nullable
  @Override
  public String getConnectionString(DbRequest request) {
    return request.getDbInfo().getDbConnectionString();
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
    if (error instanceof SQLException) {
      int errorCode = ((SQLException) error).getErrorCode();
      return errorCode == 0 ? null : Integer.toString(errorCode);
    }
    return null;
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
    String addressGroup = dbInfo.getServerAddressGroup();
    if (emitStableDatabaseSemconv() && addressGroup != null) {
      return addressGroup;
    }
    return dbInfo.getServerAddress();
  }

  @Nullable
  @Override
  public Integer getServerPort(DbRequest request) {
    DbInfo dbInfo = request.getDbInfo();
    if (emitStableDatabaseSemconv() && dbInfo.getServerAddressGroup() != null) {
      // the group target already carries the port of every host it names
      return null;
    }
    return dbInfo.getServerPort();
  }
}
