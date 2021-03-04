/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api.db;

import com.google.auto.value.AutoValue;
import org.checkerframework.checker.nullness.qual.Nullable;

@AutoValue
public abstract class SqlStatementInfo {

  public static SqlStatementInfo create(
      @Nullable String fullStatement, @Nullable String operation, @Nullable String table) {
    return new AutoValue_SqlStatementInfo(fullStatement, operation, table);
  }

  @Nullable
  public abstract String getFullStatement();

  @Nullable
  public abstract String getOperation();

  @Nullable
  public abstract String getTable();
}
