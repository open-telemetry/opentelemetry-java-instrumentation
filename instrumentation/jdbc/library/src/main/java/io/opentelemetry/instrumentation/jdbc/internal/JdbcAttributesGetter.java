/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import io.opentelemetry.instrumentation.api.instrumenter.db.SqlClientAttributesGetter;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class JdbcAttributesGetter implements SqlClientAttributesGetter<DbRequest> {

  @Nullable
  @Override
  public String system(DbRequest request) {
    return request.getDbInfo().getSystem();
  }

  @Nullable
  @Override
  public String user(DbRequest request) {
    return request.getDbInfo().getUser();
  }

  @Nullable
  @Override
  public String name(DbRequest request) {
    DbInfo dbInfo = request.getDbInfo();
    return dbInfo.getName() == null ? dbInfo.getDb() : dbInfo.getName();
  }

  @Nullable
  @Override
  public String connectionString(DbRequest request) {
    return request.getDbInfo().getShortUrl();
  }

  @Nullable
  @Override
  public String rawStatement(DbRequest request) {
    return request.getStatement();
  }
}
