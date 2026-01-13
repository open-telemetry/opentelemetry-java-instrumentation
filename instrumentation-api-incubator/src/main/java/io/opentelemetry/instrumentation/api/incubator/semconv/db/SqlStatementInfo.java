/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;

@AutoValue
public abstract class SqlStatementInfo {

  public static SqlStatementInfo create(
      @Nullable String queryText,
      @Nullable String operationName,
      @Nullable String collectionName,
      @Nullable String storedProcedureName) {
    return new AutoValue_SqlStatementInfo(
        queryText, operationName, collectionName, storedProcedureName);
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

  /** Returns the table/collection name, or null for stored procedures. */
  @Nullable
  public abstract String getCollectionName();

  /** Returns the stored procedure name, or null for other operations. */
  @Nullable
  public abstract String getStoredProcedureName();

  /**
   * @deprecated Use {@link #getCollectionName()} or {@link #getStoredProcedureName()} instead.
   */
  @Deprecated
  @Nullable
  public String getMainIdentifier() {
    return getCollectionName() != null ? getCollectionName() : getStoredProcedureName();
  }
}
