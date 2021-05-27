/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.instrumenter.db.SqlAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.checkerframework.checker.nullness.qual.Nullable;

final class JdbcAttributesExtractor extends SqlAttributesExtractor<DbRequest, Void> {
  @Nullable
  @Override
  protected String system(DbRequest dbRequest) {
    return dbRequest.getDbInfo().getSystem();
  }

  @Nullable
  @Override
  protected String user(DbRequest dbRequest) {
    return dbRequest.getDbInfo().getUser();
  }

  @Nullable
  @Override
  protected String name(DbRequest dbRequest) {
    DbInfo dbInfo = dbRequest.getDbInfo();
    return dbInfo.getName() == null ? dbInfo.getDb() : dbInfo.getName();
  }

  @Nullable
  @Override
  protected String connectionString(DbRequest dbRequest) {
    return dbRequest.getDbInfo().getShortUrl();
  }

  @Override
  protected AttributeKey<String> dbTableAttribute() {
    return SemanticAttributes.DB_SQL_TABLE;
  }

  @Nullable
  @Override
  protected String rawStatement(DbRequest dbRequest) {
    return dbRequest.getStatement();
  }
}
