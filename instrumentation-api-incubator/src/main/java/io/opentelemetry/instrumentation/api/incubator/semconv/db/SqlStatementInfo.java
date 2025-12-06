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

  public static SqlStatementInfo create(
      @Nullable String queryText,
      @Nullable String operationName,
      @Nullable String target,
      @Nullable String querySummary) {
    String truncatedQuerySummary = truncateQuerySummary(querySummary);
    return new AutoValue_SqlStatementInfo(
        queryText, operationName, target, truncatedQuerySummary);
  }

  /**
   * Truncates the query summary to {@link #QUERY_SUMMARY_MAX_LENGTH} characters, ensuring
   * truncation does not occur within an operation name or target.
   */
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

  /**
   * @deprecated Use {@link #getQueryText()} instead.
   */
  @Deprecated
  @Nullable
  public String getFullStatement() {
    return getQueryText();
  }

  @Nullable
  public abstract String getOperationName();

  /**
   * @deprecated Use {@link #getOperationName()} instead.
   */
  @Deprecated
  @Nullable
  public String getOperation() {
    return getOperationName();
  }

  /**
   * Returns the table/collection name, or null for CALL operations.
   *
   * @see #getStoredProcedureName()
   */
  @Nullable
  public String getCollectionName() {
    return SQL_CALL.equals(getOperationName()) ? null : getTarget();
  }

  /** Returns the stored procedure name for CALL operations, or null for other operations. */
  @Nullable
  public String getStoredProcedureName() {
    return SQL_CALL.equals(getOperationName()) ? getTarget() : null;
  }

  /**
   * Returns the main identifier from the SQL statement - either the table/collection name or stored
   * procedure name depending on the operation.
   *
   * <p>For setting the {@code db.collection.name} attribute, use {@link #getCollectionName()}
   * instead which returns null for CALL operations.
   *
   * @deprecated Use {@link #getCollectionName()} for db.collection.name attribute, or {@link
   *     #getStoredProcedureName()} for stored procedure name. This method may be used for span
   *     names where both table and procedure names are needed.
   */
  @Deprecated
  @Nullable
  public String getMainIdentifier() {
    return getTarget();
  }

  @Nullable
  abstract String getTarget();

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
