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
      @Nullable String queryText, @Nullable String operationName, @Nullable String collectionName) {
    return new AutoValue_SqlStatementInfo(queryText, operationName, collectionName, null);
  }

  public static SqlStatementInfo createStoredProcedure(
      @Nullable String queryText,
      @Nullable String operationName,
      @Nullable String storedProcedureName) {
    return new AutoValue_SqlStatementInfo(queryText, operationName, null, storedProcedureName);
  }

  @Nullable
  public abstract String getQueryText();

  @Nullable
  public abstract String getOperationName();

  /** Returns the table/collection name, or null for stored procedure operations. */
  @Nullable
  public abstract String getCollectionName();

  /** Returns the stored procedure name, or null for other operations. */
  @Nullable
  public abstract String getStoredProcedureName();
}
