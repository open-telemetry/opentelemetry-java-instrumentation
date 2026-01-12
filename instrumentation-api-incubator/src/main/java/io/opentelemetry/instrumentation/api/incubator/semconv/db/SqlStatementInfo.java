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
      @Nullable String queryText, @Nullable String operationName, @Nullable String mainIdentifier) {
    return new AutoValue_SqlStatementInfo(queryText, operationName, mainIdentifier);
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

  @Nullable
  public abstract String getMainIdentifier();
}
