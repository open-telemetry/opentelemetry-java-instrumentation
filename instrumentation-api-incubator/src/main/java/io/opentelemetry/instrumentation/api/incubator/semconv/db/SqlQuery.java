/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static io.opentelemetry.instrumentation.api.internal.StringUtils.truncate;

import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;

@AutoValue
public abstract class SqlQuery {

  private static final String SQL_CALL = "CALL";
  private static final int QUERY_SUMMARY_MAX_LENGTH = 255;

  /**
   * Creates a SQL analysis result with operation, collection, stored procedure, and summary values.
   *
   * <p>The query text is used as supplied; the summary is truncated to at most 255 characters.
   */
  public static SqlQuery create(
      @Nullable String queryText,
      @Nullable String operationName,
      @Nullable String collectionName,
      @Nullable String storedProcedureName,
      @Nullable String querySummary) {
    String truncatedQuerySummary = truncateQuerySummary(querySummary);
    return new AutoValue_SqlQuery(
        queryText, operationName, collectionName, storedProcedureName, truncatedQuerySummary);
  }

  /** Creates a SQL analysis result with an operation and its table or stored procedure target. */
  public static SqlQuery create(
      @Nullable String queryText, @Nullable String operationName, @Nullable String target) {
    boolean isStoredProcedure = SQL_CALL.equals(operationName) || "EXECUTE".equals(operationName);
    String collectionName = isStoredProcedure ? null : target;
    String storedProcedureName = isStoredProcedure ? target : null;
    return create(queryText, operationName, collectionName, storedProcedureName, null);
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
    return truncate(querySummary, QUERY_SUMMARY_MAX_LENGTH);
  }

  @Nullable
  public abstract String getQueryText();

  /** Returns the parsed operation name, or null when no operation was analyzed. */
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
