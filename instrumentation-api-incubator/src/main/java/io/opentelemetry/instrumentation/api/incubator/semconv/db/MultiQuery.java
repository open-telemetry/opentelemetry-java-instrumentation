/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.Nullable;

class MultiQuery {

  @Nullable private final String collectionName;
  @Nullable private final String storedProcedureName;
  @Nullable private final String operationName;
  private final Set<String> queryTexts;

  private MultiQuery(
      @Nullable String collectionName,
      @Nullable String storedProcedureName,
      @Nullable String operationName,
      Set<String> queryTexts) {
    this.collectionName = collectionName;
    this.storedProcedureName = storedProcedureName;
    this.operationName = operationName;
    this.queryTexts = queryTexts;
  }

  static MultiQuery analyze(
      Collection<String> rawQueryTexts, boolean statementSanitizationEnabled) {
    UniqueValue uniqueCollectionName = new UniqueValue();
    UniqueValue uniqueStoredProcedureName = new UniqueValue();
    UniqueValue uniqueOperationName = new UniqueValue();
    Set<String> uniqueQueryTexts = new LinkedHashSet<>();
    for (String rawQueryText : rawQueryTexts) {
      SqlStatementInfo sanitizedStatement = SqlStatementSanitizerUtil.sanitize(rawQueryText);
      String collectionName = sanitizedStatement.getCollectionName();
      uniqueCollectionName.set(collectionName);
      String storedProcedureName = sanitizedStatement.getStoredProcedureName();
      uniqueStoredProcedureName.set(storedProcedureName);
      String operationName = sanitizedStatement.getOperationName();
      uniqueOperationName.set(operationName);
      uniqueQueryTexts.add(
          statementSanitizationEnabled ? sanitizedStatement.getQueryText() : rawQueryText);
    }

    String operationName = uniqueOperationName.getValue();
    return new MultiQuery(
        uniqueCollectionName.getValue(),
        uniqueStoredProcedureName.getValue(),
        operationName == null ? "BATCH" : "BATCH " + operationName,
        uniqueQueryTexts);
  }

  @Nullable
  public String getCollectionName() {
    return collectionName;
  }

  @Nullable
  public String getStoredProcedureName() {
    return storedProcedureName;
  }

  /**
   * @deprecated Use {@link #getCollectionName()} or {@link #getStoredProcedureName()} instead.
   */
  @Deprecated
  @Nullable
  public String getMainIdentifier() {
    return collectionName != null ? collectionName : storedProcedureName;
  }

  @Nullable
  public String getOperationName() {
    return operationName;
  }

  /**
   * @deprecated Use {@link #getOperationName()} instead.
   */
  @Deprecated
  @Nullable
  public String getOperation() {
    return getOperationName();
  }

  public Set<String> getQueryTexts() {
    return queryTexts;
  }

  /**
   * @deprecated Use {@link #getQueryTexts()} instead.
   */
  @Deprecated
  public Set<String> getStatements() {
    return getQueryTexts();
  }

  private static class UniqueValue {
    @Nullable private String value;
    private boolean valid = true;

    void set(@Nullable String value) {
      if (!valid) {
        return;
      }
      if (this.value == null) {
        this.value = value;
      } else if (!this.value.equals(value)) {
        valid = false;
      }
    }

    @Nullable
    String getValue() {
      return valid ? value : null;
    }
  }
}
