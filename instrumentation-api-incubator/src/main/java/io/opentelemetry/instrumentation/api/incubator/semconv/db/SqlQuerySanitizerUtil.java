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
 * Helper class for sanitizing sql that keeps sanitization results in {@link InstrumenterContext} so
 * that each query would be sanitized only once for given {@link Instrumenter} call.
 */
class SqlQuerySanitizerUtil {
  private static final SqlQuerySanitizer sanitizer = SqlQuerySanitizer.create(true);

  static SqlQuery sanitize(String queryText) {
    Map<String, SqlQuery> map =
        InstrumenterContext.computeIfAbsent("sanitized-sql-map", unused -> new HashMap<>());
    return map.computeIfAbsent(queryText, sanitizer::sanitize);
  }

  static SqlQuery sanitizeWithSummary(String queryText) {
    Map<String, SqlQuery> map =
        InstrumenterContext.computeIfAbsent(
            "sanitized-sql-map-with-summary", unused -> new HashMap<>());
    return map.computeIfAbsent(queryText, sanitizer::sanitizeWithSummary);
  }

  private SqlQuerySanitizerUtil() {}
}
