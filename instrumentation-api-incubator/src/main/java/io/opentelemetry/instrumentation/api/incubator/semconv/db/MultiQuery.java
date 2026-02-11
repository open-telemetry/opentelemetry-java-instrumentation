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
  @Nullable private final String querySummary;

  private MultiQuery(
      @Nullable String collectionName,
      @Nullable String storedProcedureName,
      @Nullable String operationName,
      Set<String> queryTexts,
      @Nullable String querySummary) {
    this.collectionName = collectionName;
    this.storedProcedureName = storedProcedureName;
    this.operationName = operationName;
    this.queryTexts = queryTexts;
    this.querySummary = querySummary;
  }

  static MultiQuery analyze(Collection<String> rawQueryTexts, boolean querySanitizationEnabled) {
    return analyzeInternal(rawQueryTexts, querySanitizationEnabled, false);
  }

  static MultiQuery analyzeWithSummary(
      Collection<String> rawQueryTexts, boolean querySanitizationEnabled) {
    return analyzeInternal(rawQueryTexts, querySanitizationEnabled, true);
  }

  private static MultiQuery analyzeInternal(
      Collection<String> rawQueryTexts, boolean querySanitizationEnabled, boolean withSummary) {
    UniqueValue uniqueCollectionName = new UniqueValue();
    UniqueValue uniqueStoredProcedureName = new UniqueValue();
    UniqueValue uniqueOperationName = new UniqueValue();
    Set<String> uniqueQueryTexts = new LinkedHashSet<>();
    UniqueValue uniqueQuerySummary = new UniqueValue();
    for (String rawQueryText : rawQueryTexts) {
      SqlQuery sanitizedStatement =
          withSummary
              ? SqlQuerySanitizerUtil.sanitizeWithSummary(rawQueryText)
              : SqlQuerySanitizerUtil.sanitize(rawQueryText);
      String collectionName = sanitizedStatement.getCollectionName();
      uniqueCollectionName.set(collectionName);
      String storedProcedureName = sanitizedStatement.getStoredProcedureName();
      uniqueStoredProcedureName.set(storedProcedureName);
      String operationName = sanitizedStatement.getOperationName();
      uniqueOperationName.set(operationName);
      uniqueQueryTexts.add(
          querySanitizationEnabled ? sanitizedStatement.getQueryText() : rawQueryText);
      uniqueQuerySummary.set(sanitizedStatement.getQuerySummary());
    }

    String operationName = uniqueOperationName.getValue();
    String querySummary = uniqueQuerySummary.getValue();
    return new MultiQuery(
        uniqueCollectionName.getValue(),
        uniqueStoredProcedureName.getValue(),
        withSummary ? null : (operationName == null ? "BATCH" : "BATCH " + operationName),
        uniqueQueryTexts,
        withSummary ? (querySummary == null ? "BATCH" : "BATCH " + querySummary) : null);
  }

  @Nullable
  public String getCollectionName() {
    return collectionName;
  }

  @Nullable
  public String getStoredProcedureName() {
    return storedProcedureName;
  }

  @Nullable
  public String getOperationName() {
    return operationName;
  }

  @Nullable
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
