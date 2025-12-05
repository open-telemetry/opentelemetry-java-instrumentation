/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;

@AutoValue
public abstract class SqlStatementInfo {

  private static final int QUERY_SUMMARY_MAX_LENGTH = 255;

  public static SqlStatementInfo create(
      @Nullable String fullStatement,
      @Nullable String operation,
      @Nullable String identifier,
      @Nullable String querySummary) {
    String truncatedQuerySummary = truncateQuerySummary(querySummary);
    return new AutoValue_SqlStatementInfo(
        fullStatement, operation, identifier, truncatedQuerySummary);
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
  public abstract String getFullStatement();

  @Nullable
  public abstract String getOperation();

  @Nullable
  public abstract String getMainIdentifier();

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
