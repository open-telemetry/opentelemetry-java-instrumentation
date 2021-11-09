/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.db;

import static io.opentelemetry.instrumentation.api.db.StatementSanitizationConfig.isStatementSanitizationEnabled;
import static io.opentelemetry.instrumentation.api.internal.SupportabilityMetrics.CounterNames.SQL_STATEMENT_SANITIZER_CACHE_MISS;

import io.opentelemetry.instrumentation.api.caching.Cache;
import io.opentelemetry.instrumentation.api.internal.SupportabilityMetrics;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * This class is responsible for masking potentially sensitive parameters in SQL (and SQL-like)
 * statements and queries.
 */
public final class SqlStatementSanitizer {
  private static final SupportabilityMetrics supportability = SupportabilityMetrics.instance();

  private static final Cache<StatementKey, SqlStatementInfo> sqlToStatementInfoCache =
      Cache.builder().setMaximumSize(1000).build();

  public static SqlStatementInfo sanitize(@Nullable String statement) {
    return sanitize(statement, SqlDialect.DEFAULT);
  }

  public static SqlStatementInfo sanitize(@Nullable String statement, SqlDialect dialect) {
    if (!isStatementSanitizationEnabled() || statement == null) {
      return SqlStatementInfo.create(statement, null, null);
    }
    return sqlToStatementInfoCache.computeIfAbsent(
        new StatementKey(statement, dialect),
        k -> {
          supportability.incrementCounter(SQL_STATEMENT_SANITIZER_CACHE_MISS);
          return AutoSqlSanitizer.sanitize(statement, dialect);
        });
  }

  private static final class StatementKey {
    private final String statement;
    private final SqlDialect dialect;

    StatementKey(String statement, SqlDialect dialect) {
      this.statement = statement;
      this.dialect = dialect;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      StatementKey that = (StatementKey) o;
      return statement.equals(that.statement) && dialect == that.dialect;
    }

    @Override
    public int hashCode() {
      return Objects.hash(statement, dialect);
    }

    @Override
    public String toString() {
      return "StatementKey(statement=" + statement + ", dialect=" + dialect + ")";
    }
  }

  private SqlStatementSanitizer() {}
}
