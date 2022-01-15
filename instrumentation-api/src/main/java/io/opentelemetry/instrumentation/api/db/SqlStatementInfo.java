/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.db;

import com.google.auto.value.AutoValue;
import java.util.function.Function;
import javax.annotation.Nullable;

@AutoValue
public abstract class SqlStatementInfo {

  public static SqlStatementInfo create(
      @Nullable String fullStatement, @Nullable String operation, @Nullable String table) {
    return new AutoValue_SqlStatementInfo(fullStatement, operation, table);
  }

  public SqlStatementInfo mapTable(Function<String, String> mapper) {
    return SqlStatementInfo.create(getFullStatement(), getOperation(), mapper.apply(getTable()));
  }

  @Nullable
  public abstract String getFullStatement();

  @Nullable
  public abstract String getOperation();

  @Nullable
  public abstract String getTable();
}
