/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class JdbcAttributesGetter implements SqlClientAttributesGetter<DbRequest> {

  @Nullable
  @Override
  public String getSystem(DbRequest request) {
    return request.getDbInfo().getSystem();
  }

  @Deprecated
  @Nullable
  @Override
  public String getUser(DbRequest request) {
    return request.getDbInfo().getUser();
  }

  @Deprecated
  @Nullable
  @Override
  public String getName(DbRequest request) {
    DbInfo dbInfo = request.getDbInfo();
    return dbInfo.getName() == null ? dbInfo.getDb() : dbInfo.getName();
  }

  @Nullable
  @Override
  public String getNamespace(DbRequest request) {
    DbInfo dbInfo = request.getDbInfo();
    return dbInfo.getName() == null ? dbInfo.getDb() : dbInfo.getName();
  }

  @Deprecated
  @Nullable
  @Override
  public String getConnectionString(DbRequest request) {
    return request.getDbInfo().getShortUrl();
  }

  @Deprecated
  @Nullable
  @Override
  public String getRawStatement(DbRequest request) {
    return request.getDbQueryText();
  }

  @Override
  public String getRawQueryText(DbRequest request) {
    return request.getDbQueryText();
  }
}
