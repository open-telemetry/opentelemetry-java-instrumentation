/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.ExtractQuerySummaryMarker;
import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class JdbcAttributesGetter
    implements SqlClientAttributesGetter<DbRequest, Void>, ExtractQuerySummaryMarker {

  public static final JdbcAttributesGetter INSTANCE = new JdbcAttributesGetter();

  @Nullable
  @Override
  public String getDbSystemName(DbRequest request) {
    return request.getDbInfo().getSystem();
  }

  @Deprecated // to be removed in 3.0
  @Nullable
  @Override
  public String getUser(DbRequest request) {
    return request.getDbInfo().getUser();
  }

  @Nullable
  @Override
  public String getDbNamespace(DbRequest request) {
    DbInfo dbInfo = request.getDbInfo();
    return dbInfo.getName() == null ? dbInfo.getDb() : dbInfo.getName();
  }

  @Deprecated // to be removed in 3.0
  @Nullable
  @Override
  public String getConnectionString(DbRequest request) {
    return request.getDbInfo().getShortUrl();
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
  public String getDbResponseStatusCode(@Nullable Void response, @Nullable Throwable error) {
    if (error instanceof SQLException) {
      return Integer.toString(((SQLException) error).getErrorCode());
    }
    return null;
  }

  @Override
  public Map<String, String> getDbQueryParameters(DbRequest request) {
    return request.getPreparedStatementParameters();
  }

  @Override
  public boolean isParameterizedQuery(DbRequest request) {
    return request.isParameterizedQuery();
  }

  @Nullable
  @Override
  public String getServerAddress(DbRequest request) {
    return request.getDbInfo().getHost();
  }

  @Nullable
  @Override
  public Integer getServerPort(DbRequest request) {
    return request.getDbInfo().getPort();
  }
}
