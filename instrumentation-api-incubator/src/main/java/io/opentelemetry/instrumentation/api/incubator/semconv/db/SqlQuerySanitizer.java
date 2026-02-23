/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_STRING_LITERALS;
import static io.opentelemetry.instrumentation.api.internal.SupportabilityMetrics.CounterNames.SQL_SANITIZER_CACHE_MISS;

import com.google.auto.value.AutoValue;
import io.opentelemetry.instrumentation.api.internal.SupportabilityMetrics;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import javax.annotation.Nullable;

/**
 * This class is responsible for masking potentially sensitive parameters in SQL (and SQL-like)
 * statements and queries.
 */
public final class SqlQuerySanitizer {
  private static final SupportabilityMetrics supportability = SupportabilityMetrics.instance();

  private static final Cache<CacheKey, SqlQuery> sqlToQueryCache = Cache.bounded(1000);
  private static final Cache<CacheKey, SqlQuery> sqlToQueryCacheWithSummary = Cache.bounded(1000);
  private static final int LARGE_QUERY_THRESHOLD = 10 * 1024;

  public static SqlQuerySanitizer create(boolean querySanitizationEnabled) {
    return new SqlQuerySanitizer(querySanitizationEnabled);
  }

  private final boolean querySanitizationEnabled;

  private SqlQuerySanitizer(boolean querySanitizationEnabled) {
    this.querySanitizationEnabled = querySanitizationEnabled;
  }

  /**
   * @deprecated Use {@link #sanitize(String, SqlDialect)} and pass an explicit dialect.
   */
  @Deprecated
  public SqlQuery sanitize(@Nullable String query) {
    return sanitize(query, DOUBLE_QUOTES_ARE_STRING_LITERALS);
  }

  public SqlQuery sanitize(@Nullable String query, SqlDialect dialect) {
    if (!querySanitizationEnabled || query == null) {
      return SqlQuery.create(query, null, null);
    }
    // sanitization result will not be cached for queries larger than the threshold to avoid
    // cache growing too large
    // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/13180
    if (query.length() > LARGE_QUERY_THRESHOLD) {
      return sanitizeImpl(query, dialect);
    }
    return sqlToQueryCache.computeIfAbsent(
        CacheKey.create(query, dialect), k -> sanitizeImpl(query, dialect));
  }

  private static SqlQuery sanitizeImpl(String query, SqlDialect dialect) {
    supportability.incrementCounter(SQL_SANITIZER_CACHE_MISS);
    return AutoSqlSanitizer.sanitize(query, dialect);
  }

  /**
   * Sanitize and extract query summary.
   *
   * @deprecated Use {@link #sanitizeWithSummary(String, SqlDialect)} and pass an explicit dialect.
   */
  @Deprecated
  public SqlQuery sanitizeWithSummary(@Nullable String query) {
    return sanitizeWithSummary(query, DOUBLE_QUOTES_ARE_STRING_LITERALS);
  }

  /** Sanitize and extract query summary. */
  public SqlQuery sanitizeWithSummary(@Nullable String query, SqlDialect dialect) {
    if (!querySanitizationEnabled || query == null) {
      return SqlQuery.createWithSummary(query, null, null);
    }
    // sanitization result will not be cached for queries larger than the threshold to avoid
    // cache growing too large
    // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/13180
    if (query.length() > LARGE_QUERY_THRESHOLD) {
      return sanitizeWithSummaryImpl(query, dialect);
    }
    return sqlToQueryCacheWithSummary.computeIfAbsent(
        CacheKey.create(query, dialect), k -> sanitizeWithSummaryImpl(query, dialect));
  }

  private static SqlQuery sanitizeWithSummaryImpl(String query, SqlDialect dialect) {
    supportability.incrementCounter(SQL_SANITIZER_CACHE_MISS);
    return AutoSqlSanitizerWithSummary.sanitize(query, dialect);
  }

  // visible for tests
  static boolean isCached(String query) {
    return sqlToQueryCache.get(CacheKey.create(query, DOUBLE_QUOTES_ARE_STRING_LITERALS)) != null;
  }

  @AutoValue
  abstract static class CacheKey {

    static CacheKey create(String queryText, SqlDialect dialect) {
      return new AutoValue_SqlQuerySanitizer_CacheKey(queryText, dialect);
    }

    abstract String getQueryText();

    abstract SqlDialect getDialect();
  }
}
