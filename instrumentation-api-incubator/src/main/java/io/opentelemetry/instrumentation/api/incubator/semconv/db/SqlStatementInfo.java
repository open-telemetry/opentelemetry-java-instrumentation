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

  public static SqlStatementInfo create(
      @Nullable String queryText, @Nullable String operationName, @Nullable String target) {
    return new AutoValue_SqlStatementInfo(queryText, operationName, target);
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
    return SQL_CALL.equalsIgnoreCase(getOperationName()) ? null : getTarget();
  }

  /** Returns the stored procedure name for CALL operations, or null for other operations. */
  @Nullable
  public String getStoredProcedureName() {
    return SQL_CALL.equalsIgnoreCase(getOperationName()) ? getTarget() : null;
  }

  /**
   * @deprecated Use {@link #getCollectionName()} or {@link #getStoredProcedureName()} instead.
   */
  @Deprecated
  @Nullable
  public String getMainIdentifier() {
    return getCollectionName() != null ? getCollectionName() : getStoredProcedureName();
  }

  @Nullable
  abstract String getTarget();
}
