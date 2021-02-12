/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api.db;

import static io.opentelemetry.javaagent.instrumentation.api.db.StatementSanitizationConfig.isStatementSanitizationEnabled;

import io.opentelemetry.javaagent.instrumentation.api.BoundedCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is responsible for masking potentially sensitive parameters in SQL (and SQL-like)
 * statements and queries.
 */
public final class SqlStatementSanitizer {
  private static final Logger log = LoggerFactory.getLogger(SqlStatementSanitizer.class);

  private static final BoundedCache<String, SqlStatementInfo> sqlToStatementInfoCache =
      BoundedCache.build(1000);

  public static SqlStatementInfo sanitize(String statement) {
    if (!isStatementSanitizationEnabled() || statement == null) {
      return new SqlStatementInfo(statement, null, null);
    }
    return sqlToStatementInfoCache.get(
        statement,
        k -> {
          log.trace("SQL statement cache miss");
          return AutoSqlSanitizer.sanitize(statement);
        });
  }

  private SqlStatementSanitizer() {}
}
