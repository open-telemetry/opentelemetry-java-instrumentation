/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.db;

import static io.opentelemetry.instrumentation.api.db.StatementSanitizationConfig.isStatementSanitizationEnabled;
import static io.opentelemetry.instrumentation.api.internal.SupportabilityMetrics.CounterNames.SQL_STATEMENT_SANITIZER_CACHE_MISS;

import io.opentelemetry.instrumentation.api.caching.Cache;
import io.opentelemetry.instrumentation.api.internal.SupportabilityMetrics;
import javax.annotation.Nullable;

/**
 * This class is responsible for masking potentially sensitive parameters in SQL (and SQL-like)
 * statements and queries.
 */
public final class SqlStatementSanitizer {
  private static final SupportabilityMetrics supportability = SupportabilityMetrics.instance();

  private static final Cache<String, SqlStatementInfo> sqlToStatementInfoCache =
      Cache.newBuilder().setMaximumSize(1000).build();

  public static SqlStatementInfo sanitize(@Nullable String statement) {
    if (!isStatementSanitizationEnabled() || statement == null) {
      return SqlStatementInfo.create(statement, null, null);
    }
    return sqlToStatementInfoCache.computeIfAbsent(
        statement,
        k -> {
          supportability.incrementCounter(SQL_STATEMENT_SANITIZER_CACHE_MISS);
          return AutoSqlSanitizer.sanitize(statement);
        });
  }

  private SqlStatementSanitizer() {}
}
