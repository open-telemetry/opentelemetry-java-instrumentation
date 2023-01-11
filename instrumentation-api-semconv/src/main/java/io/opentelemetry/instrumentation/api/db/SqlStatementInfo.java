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
      @Nullable String fullStatement, @Nullable String operation, @Nullable String identifier) {
    return new AutoValue_SqlStatementInfo(fullStatement, operation, identifier);
  }

  public SqlStatementInfo mapTable(Function<String, String> mapper) {
    return SqlStatementInfo.create(
        getFullStatement(), getOperation(), mapper.apply(getIdentifier()));
  }

  @Nullable
  public abstract String getFullStatement();

  @Nullable
  public abstract String getOperation();

  @Nullable
  public abstract String getIdentifier();

  @Nullable
  public String getTable() {
    String operation = getOperation();
    if (operation != null && !operation.equals("CALL")) {
      return getIdentifier();
    }
    return null;
  }
}
