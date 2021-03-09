/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.db;

import static io.opentelemetry.instrumentation.api.db.StatementSanitizationConfig.isStatementSanitizationEnabled;

import io.opentelemetry.instrumentation.api.caching.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is responsible for masking potentially sensitive parameters in SQL (and SQL-like)
 * statements and queries.
 */
public final class SqlStatementSanitizer {
  private static final Logger log = LoggerFactory.getLogger(SqlStatementSanitizer.class);

  private static final Cache<String, SqlStatementInfo> sqlToStatementInfoCache =
      Cache.newBuilder().setMaximumSize(1000).build();

  public static SqlStatementInfo sanitize(String statement) {
    if (!isStatementSanitizationEnabled() || statement == null) {
      return SqlStatementInfo.create(statement, null, null);
    }
    return sqlToStatementInfoCache.computeIfAbsent(
        statement,
        k -> {
          log.trace("SQL statement cache miss");
          return AutoSqlSanitizer.sanitize(statement);
        });
  }

  private SqlStatementSanitizer() {}
}
