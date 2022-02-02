/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.instrumenter.db.SqlAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class JdbcAttributesExtractor extends SqlAttributesExtractor<DbRequest, Void> {
  @Nullable
  @Override
  protected String system(DbRequest request) {
    return request.getDbInfo().getSystem();
  }

  @Nullable
  @Override
  protected String user(DbRequest request) {
    return request.getDbInfo().getUser();
  }

  @Nullable
  @Override
  protected String name(DbRequest request) {
    DbInfo dbInfo = request.getDbInfo();
    return dbInfo.getName() == null ? dbInfo.getDb() : dbInfo.getName();
  }

  @Nullable
  @Override
  protected String connectionString(DbRequest request) {
    return request.getDbInfo().getShortUrl();
  }

  @Override
  protected AttributeKey<String> dbTableAttribute() {
    return SemanticAttributes.DB_SQL_TABLE;
  }

  @Nullable
  @Override
  protected String rawStatement(DbRequest request) {
    return request.getStatement();
  }
}
