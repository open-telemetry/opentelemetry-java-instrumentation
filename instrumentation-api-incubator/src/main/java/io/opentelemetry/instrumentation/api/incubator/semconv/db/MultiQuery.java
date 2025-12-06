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
  private final String operationName;
  private final String querySummary;
  private final Set<String> queryTexts;

  private MultiQuery(
      @Nullable String collectionName,
      String operationName,
      String querySummary,
      Set<String> queryTexts) {
    this.collectionName = collectionName;
    this.operationName = operationName;
    this.querySummary = querySummary;
    this.queryTexts = queryTexts;
  }

  static MultiQuery analyze(
      Collection<String> rawQueryTexts, boolean statementSanitizationEnabled) {
    UniqueValue uniqueCollectionName = new UniqueValue();
    UniqueValue uniqueOperationName = new UniqueValue();
    UniqueValue uniqueQuerySummary = new UniqueValue();
    Set<String> uniqueQueryTexts = new LinkedHashSet<>();
    for (String rawQueryText : rawQueryTexts) {
      SqlStatementInfo sanitizedStatement = SqlStatementSanitizerUtil.sanitize(rawQueryText);
      uniqueCollectionName.set(sanitizedStatement.getCollectionName());
      uniqueOperationName.set(sanitizedStatement.getOperationName());
      uniqueQuerySummary.set(sanitizedStatement.getQuerySummary());
      uniqueQueryTexts.add(
          statementSanitizationEnabled ? sanitizedStatement.getQueryText() : rawQueryText);
    }

    String operationName = uniqueOperationName.getValue();
    String querySummary = uniqueQuerySummary.getValue();

    String collectionName = uniqueCollectionName.getValue();
    String batchOperationName = operationName != null ? "BATCH " + operationName : "BATCH";
    String batchQuerySummary = querySummary != null ? "BATCH " + querySummary : batchOperationName;

    return new MultiQuery(collectionName, batchOperationName, batchQuerySummary, uniqueQueryTexts);
  }

  @Nullable
  public String getCollectionName() {
    return collectionName;
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
