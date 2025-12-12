/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlStatementSanitizer.CacheKey;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.InstrumenterContext;
import io.opentelemetry.semconv.DbAttributes;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Helper class for sanitizing sql that keeps sanitization results in {@link InstrumenterContext} so
 * that each statement would be sanitized only once for given {@link Instrumenter} call.
 */
class SqlStatementSanitizerUtil {
  private static final SqlStatementSanitizer sanitizer = SqlStatementSanitizer.create(true);
  private static final Set<String> dbsWithAnsiQuotes =
      new HashSet<>(
          Arrays.asList(
              DbAttributes.DbSystemNameValues.POSTGRESQL,
              "oracle",
              "h2",
              "hsqldb",
              "db2",
              "derby",
              "hanadb"));

  static SqlStatementInfo sanitize(String queryText, SqlDialect dialect) {
    Map<CacheKey, SqlStatementInfo> map =
        InstrumenterContext.computeIfAbsent("sanitized-sql-map", unused -> new HashMap<>());
    return map.computeIfAbsent(
        CacheKey.create(queryText, dialect),
        key -> sanitizer.sanitize(key.getStatement(), key.getDialect()));
  }

  static SqlDialect getDialect(String dbSystem, boolean ansiQuotes) {
    return ansiQuotes || dbsWithAnsiQuotes.contains(dbSystem)
        ? SqlDialect.ANSI_QUOTES
        : SqlDialect.DEFAULT;
  }

  private SqlStatementSanitizerUtil() {}
}
