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
  private final String operationName;
  private final String querySummary;
  private final Set<String> queryTexts;

  private MultiQuery(
      @Nullable String collectionName,
      @Nullable String storedProcedureName,
      String operationName,
      String querySummary,
      Set<String> queryTexts) {
    this.collectionName = collectionName;
    this.storedProcedureName = storedProcedureName;
    this.operationName = operationName;
    this.querySummary = querySummary;
    this.queryTexts = queryTexts;
  }

  static MultiQuery analyze(
      Collection<String> rawQueryTexts, boolean statementSanitizationEnabled) {
    UniqueValue uniqueCollectionName = new UniqueValue();
    UniqueValue uniqueStoredProcedureName = new UniqueValue();
    UniqueValue uniqueOperationName = new UniqueValue();
    UniqueValue uniqueQuerySummary = new UniqueValue();
    Set<String> uniqueQueryTexts = new LinkedHashSet<>();
    for (String rawQueryText : rawQueryTexts) {
      SqlStatementInfo sanitizedStatement = SqlStatementSanitizerUtil.sanitize(rawQueryText);
      String collectionName = sanitizedStatement.getCollectionName();
      uniqueCollectionName.set(collectionName);
      String storedProcedureName = sanitizedStatement.getStoredProcedureName();
      uniqueStoredProcedureName.set(storedProcedureName);
      String operationName = sanitizedStatement.getOperationName();
      uniqueOperationName.set(operationName);
      uniqueQuerySummary.set(sanitizedStatement.getQuerySummary());
      uniqueQueryTexts.add(
          statementSanitizationEnabled ? sanitizedStatement.getQueryText() : rawQueryText);
    }

    String operationName = uniqueOperationName.getValue();
    String querySummary = uniqueQuerySummary.getValue();
    return new MultiQuery(
        uniqueCollectionName.getValue(),
        uniqueStoredProcedureName.getValue(),
        operationName == null ? "BATCH" : "BATCH " + operationName,
        querySummary == null ? "BATCH" : "BATCH " + querySummary,
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

  public String getOperationName() {
    return operationName;
  }

  public String getQuerySummary() {
    return querySummary;
  }

  public Set<String> getQueryTexts() {
    return queryTexts;
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
