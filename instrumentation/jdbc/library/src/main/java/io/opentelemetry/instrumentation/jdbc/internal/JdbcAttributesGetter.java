/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;
import javax.annotation.Nullable;

final class JdbcAttributesGetter implements SqlClientAttributesGetter<DbRequest, Void> {

  @Nullable
  @Override
  public String getDbSystem(DbRequest request) {
    return request.getDbInfo().getSystem();
  }

  @Deprecated
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

  @Deprecated
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
  public Long getBatchSize(DbRequest request) {
    return request.getBatchSize();
  }

  @Nullable
  @Override
  public String getResponseStatus(@Nullable Void response, @Nullable Throwable error) {
    if (error instanceof SQLException) {
      return Integer.toString(((SQLException) error).getErrorCode());
    }
    return null;
  }

  @Override
  public Map<String, String> getQueryParameters(DbRequest request) {
    return request.getPreparedStatementParameters();
  }
}
