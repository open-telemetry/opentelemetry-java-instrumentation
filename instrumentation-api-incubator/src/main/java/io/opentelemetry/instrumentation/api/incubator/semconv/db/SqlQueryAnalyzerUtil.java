/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.InstrumenterContext;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper class for analyzing sql that keeps analysis results in {@link InstrumenterContext} so that
 * each query would be analyzed only once for given {@link Instrumenter} call.
 */
class SqlQueryAnalyzerUtil {
  private static final SqlQueryAnalyzer analyzer = SqlQueryAnalyzer.create(true);

  static SqlQuery analyze(String queryText) {
    Map<String, SqlQuery> map =
        InstrumenterContext.computeIfAbsent("sanitized-sql-map", unused -> new HashMap<>());
    return map.computeIfAbsent(queryText, analyzer::analyze);
  }

  static SqlQuery analyzeWithSummary(String queryText) {
    Map<String, SqlQuery> map =
        InstrumenterContext.computeIfAbsent(
            "sanitized-sql-map-with-summary", unused -> new HashMap<>());
    return map.computeIfAbsent(queryText, analyzer::analyzeWithSummary);
  }

  private SqlQueryAnalyzerUtil() {}
}
