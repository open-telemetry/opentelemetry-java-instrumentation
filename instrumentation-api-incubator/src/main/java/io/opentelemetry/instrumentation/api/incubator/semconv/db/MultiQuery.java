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

  @Nullable private final String storedProcedureName;
  private final Set<String> queryTexts;
  @Nullable private final String querySummary;
  @Nullable private final String operationName;
  @Nullable private final String collectionName;

  private MultiQuery(
      @Nullable String storedProcedureName,
      Set<String> queryTexts,
      @Nullable String querySummary,
      @Nullable String operationName,
      @Nullable String collectionName) {
    this.storedProcedureName = storedProcedureName;
    this.queryTexts = queryTexts;
    this.querySummary = querySummary;
    this.operationName = operationName;
    this.collectionName = collectionName;
  }

  static MultiQuery analyzeWithSummary(Collection<String> rawQueryTexts, SqlDialect dialect) {
    Builder builder = builder();
    for (String rawQueryText : rawQueryTexts) {
      SqlQuery analyzedQuery = SqlQueryAnalyzerUtil.analyzeWithSummary(rawQueryText, dialect);
      builder.add(analyzedQuery, rawQueryText);
    }

    return builder.build();
  }

  static Builder builder() {
    return new Builder();
  }

  @Nullable
  public String getStoredProcedureName() {
    return storedProcedureName;
  }

  @Nullable
  public String getQuerySummary() {
    return querySummary;
  }

  @Nullable
  public String getOperationName() {
    return operationName;
  }

  @Nullable
  public String getCollectionName() {
    return collectionName;
  }

  public Set<String> getQueryTexts() {
    return queryTexts;
  }

  static class Builder {
    private final UniqueValue uniqueStoredProcedureName = new UniqueValue();
    private final Set<String> uniqueQueryTexts = new LinkedHashSet<>();
    private final UniqueValue uniqueQuerySummary = new UniqueValue();
    private final UniqueValue uniqueOperationName = new UniqueValue();
    private final UniqueValue uniqueCollectionName = new UniqueValue();

    @SuppressWarnings(
        "deprecation") // getOperationName()/getCollectionName() package-private in 3.0
    void add(SqlQuery analyzedQuery, @Nullable String queryText) {
      uniqueStoredProcedureName.set(analyzedQuery.getStoredProcedureName());
      uniqueQueryTexts.add(queryText);
      uniqueQuerySummary.set(analyzedQuery.getQuerySummary());
      uniqueOperationName.set(analyzedQuery.getOperationName());
      uniqueCollectionName.set(analyzedQuery.getCollectionName());
    }

    MultiQuery build() {
      String querySummary = uniqueQuerySummary.getValue();
      String operationName = uniqueOperationName.getValue();
      return new MultiQuery(
          uniqueStoredProcedureName.getValue(),
          uniqueQueryTexts,
          querySummary == null ? "BATCH" : "BATCH " + querySummary,
          operationName == null ? "BATCH" : "BATCH " + operationName,
          uniqueCollectionName.getValue());
    }
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
