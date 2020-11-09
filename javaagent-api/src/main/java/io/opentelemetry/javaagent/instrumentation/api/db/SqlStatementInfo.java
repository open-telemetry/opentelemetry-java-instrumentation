/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api.db;

import java.util.Objects;
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
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SqlStatementInfo that = (SqlStatementInfo) o;
    return Objects.equals(fullStatement, that.fullStatement)
        && Objects.equals(operation, that.operation)
        && Objects.equals(table, that.table);
  }

  @Override
  public int hashCode() {
    return Objects.hash(fullStatement, operation, table);
  }
}
