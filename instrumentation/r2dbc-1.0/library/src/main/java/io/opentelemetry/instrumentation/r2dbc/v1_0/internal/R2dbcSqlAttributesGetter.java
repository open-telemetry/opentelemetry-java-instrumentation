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

  private static final String POSTGRESQL_DATABASE = "postgresql";
  private static final String POSTGRESQL_CLOSED_CONNECTION_EXCEPTION =
      "io.r2dbc.postgresql.client.ReactorNettyClient$PostgresConnectionClosedException";
  private static final String POSTGRESQL_CONNECTION_FAILURE = "08006";

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
    if (POSTGRESQL_DATABASE.equals(getDbSystemName(request))
        && error != null
        && POSTGRESQL_CLOSED_CONNECTION_EXCEPTION.equals(error.getClass().getName())
        && POSTGRESQL_CONNECTION_FAILURE.equals(getSqlState(error))) {
      return null;
    }
    return getSqlState(error);
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
