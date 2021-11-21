/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.db;

import static io.opentelemetry.instrumentation.api.db.StatementSanitizationConfig.isStatementSanitizationEnabled;
import static io.opentelemetry.instrumentation.api.internal.SupportabilityMetrics.CounterNames.SQL_STATEMENT_SANITIZER_CACHE_MISS;

import com.google.auto.value.AutoValue;
import io.opentelemetry.instrumentation.api.internal.SupportabilityMetrics;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

/**
 * This class is responsible for masking potentially sensitive parameters in SQL (and SQL-like)
 * statements and queries.
 */
public final class SqlStatementSanitizer {
  private static final SupportabilityMetrics supportability = SupportabilityMetrics.instance();

  // TODO use CHLM
  private static final ConcurrentHashMap<CacheKey, SqlStatementInfo> sqlToStatementInfoCache =
      new ConcurrentHashMap<>();

  public static SqlStatementInfo sanitize(@Nullable String statement) {
    return sanitize(statement, SqlDialect.DEFAULT);
  }

  public static SqlStatementInfo sanitize(@Nullable String statement, SqlDialect dialect) {
    if (!isStatementSanitizationEnabled() || statement == null) {
      return SqlStatementInfo.create(statement, null, null);
    }
    return sqlToStatementInfoCache.computeIfAbsent(
        CacheKey.create(statement, dialect),
        k -> {
          supportability.incrementCounter(SQL_STATEMENT_SANITIZER_CACHE_MISS);
          return AutoSqlSanitizer.sanitize(statement, dialect);
        });
  }

  @AutoValue
  abstract static class CacheKey {

    static CacheKey create(String statement, SqlDialect dialect) {
      return new AutoValue_SqlStatementSanitizer_CacheKey(statement, dialect);
    }

    abstract String getStatement();

    abstract SqlDialect getDialect();
  }

  private SqlStatementSanitizer() {}
}
