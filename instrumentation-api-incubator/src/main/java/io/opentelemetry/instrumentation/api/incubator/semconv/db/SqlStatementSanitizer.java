/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import javax.annotation.Nullable;

/**
 * This class is responsible for masking potentially sensitive parameters in SQL (and SQL-like)
 * statements and queries.
 *
 * @deprecated Use {@link SqlQuerySanitizer} instead. This class will be removed in a future
 *     release.
 */
@Deprecated
public final class SqlStatementSanitizer {

  public static SqlStatementSanitizer create(boolean statementSanitizationEnabled) {
    return new SqlStatementSanitizer(statementSanitizationEnabled);
  }

  private final boolean statementSanitizationEnabled;

  private SqlStatementSanitizer(boolean statementSanitizationEnabled) {
    this.statementSanitizationEnabled = statementSanitizationEnabled;
  }

  public SqlStatementInfo sanitize(@Nullable String statement) {
    return sanitize(statement, SqlDialect.DEFAULT);
  }

  public SqlStatementInfo sanitize(@Nullable String statement, SqlDialect dialect) {
    SqlQuery sqlQuery =
        SqlQuerySanitizer.create(statementSanitizationEnabled).sanitize(statement, dialect);
    return toStatementInfo(sqlQuery);
  }

  /** Sanitize and extract query summary. */
  public SqlStatementInfo sanitizeWithSummary(@Nullable String statement) {
    return sanitizeWithSummary(statement, SqlDialect.DEFAULT);
  }

  /** Sanitize and extract query summary. */
  public SqlStatementInfo sanitizeWithSummary(@Nullable String statement, SqlDialect dialect) {
    SqlQuery sqlQuery =
        SqlQuerySanitizer.create(statementSanitizationEnabled)
            .sanitizeWithSummary(statement, dialect);
    return toStatementInfo(sqlQuery);
  }

  private static SqlStatementInfo toStatementInfo(SqlQuery sqlQuery) {
    if (sqlQuery.getQuerySummary() != null) {
      return SqlStatementInfo.createWithSummary(
          sqlQuery.getQueryText(), sqlQuery.getStoredProcedureName(), sqlQuery.getQuerySummary());
    } else {
      return SqlStatementInfo.create(
          sqlQuery.getQueryText(),
          sqlQuery.getOperationName(),
          sqlQuery.getCollectionName() != null
              ? sqlQuery.getCollectionName()
              : sqlQuery.getStoredProcedureName());
    }
  }

  // visible for tests
  static boolean isCached(String statement) {
    return SqlQuerySanitizer.isCached(statement);
  }
}
