/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;

@AutoValue
public abstract class SqlStatementInfo {

  private static final String SQL_CALL = "CALL";
  private static final int QUERY_SUMMARY_MAX_LENGTH = 255;

  /** Creates a SqlStatementInfo for stable semconv (uses querySummary). */
  public static SqlStatementInfo createStableSemconv(
      @Nullable String queryText,
      @Nullable String storedProcedureName,
      @Nullable String querySummary) {
    String truncatedQuerySummary = truncateQuerySummary(querySummary);
    // In stable semconv: operationName and collectionName are always null
    return new AutoValue_SqlStatementInfo(
        queryText, null, null, storedProcedureName, truncatedQuerySummary);
  }

  /**
   * Creates a SqlStatementInfo for old semconv (no querySummary). Package-private for backward
   * compatibility with old jflex-generated sanitizer.
   */
  static SqlStatementInfo create(
      @Nullable String queryText, @Nullable String operationName, @Nullable String target) {
    // AutoValue constructor: (queryText, operationName, collectionName, storedProcedureName,
    // querySummary)
    // For old semconv: derive collectionName and storedProcedureName from target based on operation
    String collectionName =
        SQL_CALL.equals(operationName) || "EXECUTE".equals(operationName) ? null : target;
    String storedProcedureName =
        SQL_CALL.equals(operationName) || "EXECUTE".equals(operationName) ? target : null;
    return new AutoValue_SqlStatementInfo(
        queryText, operationName, collectionName, storedProcedureName, null);
  }

  @Nullable
  private static String truncateQuerySummary(@Nullable String querySummary) {
    if (querySummary == null || querySummary.length() <= QUERY_SUMMARY_MAX_LENGTH) {
      return querySummary;
    }
    // Truncate at the last space before the limit to avoid cutting in the middle of an identifier
    int lastSpace = querySummary.lastIndexOf(' ', QUERY_SUMMARY_MAX_LENGTH);
    if (lastSpace > 0) {
      return querySummary.substring(0, lastSpace);
    }
    // If no space found, truncate at the limit
    return querySummary.substring(0, QUERY_SUMMARY_MAX_LENGTH);
  }

  @Nullable
  public abstract String getQueryText();

  @Nullable
  public abstract String getOperationName();

  /**
   * Returns the table/collection name, or null for CALL operations.
   *
   * @see #getStoredProcedureName()
   */
  @Nullable
  public abstract String getCollectionName();

  /** Returns the stored procedure name for CALL operations, or null for other operations. */
  @Nullable
  public abstract String getStoredProcedureName();

  /**
   * Returns a low cardinality summary of the database query suitable for use as a span name or
   * metric attribute.
   *
   * <p>The summary contains operations (e.g., SELECT, INSERT) and their targets (e.g., table names)
   * in the order they appear in the query. For example:
   *
   * <ul>
   *   <li>{@code SELECT wuser_table}
   *   <li>{@code INSERT shipping_details SELECT orders}
   *   <li>{@code SELECT songs artists} (multiple tables)
   * </ul>
   *
   * @see <a
   *     href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/db/database-spans.md#generating-a-summary-of-the-query">Generating
   *     a summary of the query</a>
   */
  @Nullable
  public abstract String getQuerySummary();
}
