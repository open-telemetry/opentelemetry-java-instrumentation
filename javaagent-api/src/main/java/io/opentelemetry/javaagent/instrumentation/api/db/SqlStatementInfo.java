/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api.db;

import java.util.Objects;
import java.util.function.Function;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class SqlStatementInfo {
  @Nullable private final String fullStatement;
  @Nullable private final String operation;
  @Nullable private final String table;

  public SqlStatementInfo(
      @Nullable String fullStatement, @Nullable String operation, @Nullable String table) {
    this.fullStatement = fullStatement;
    this.operation = operation;
    this.table = table;
  }

  public SqlStatementInfo mapTable(Function<String, String> mapper) {
    return new SqlStatementInfo(fullStatement, operation, mapper.apply(table));
  }

  @Nullable
  public String getFullStatement() {
    return fullStatement;
  }

  @Nullable
  public String getOperation() {
    return operation;
  }

  @Nullable
  public String getTable() {
    return table;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof SqlStatementInfo)) {
      return false;
    }
    SqlStatementInfo other = (SqlStatementInfo) obj;
    return Objects.equals(fullStatement, other.fullStatement)
        && Objects.equals(operation, other.operation)
        && Objects.equals(table, other.table);
  }

  @Override
  public int hashCode() {
    return Objects.hash(fullStatement, operation, table);
  }
}
