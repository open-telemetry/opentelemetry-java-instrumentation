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

  private MultiQuery(
      @Nullable String storedProcedureName, Set<String> queryTexts, @Nullable String querySummary) {
    this.storedProcedureName = storedProcedureName;
    this.queryTexts = queryTexts;
    this.querySummary = querySummary;
  }

  static MultiQuery analyzeWithSummary(
      Collection<String> rawQueryTexts, boolean querySanitizationEnabled) {
    UniqueValue uniqueStoredProcedureName = new UniqueValue();
    Set<String> uniqueQueryTexts = new LinkedHashSet<>();
    UniqueValue uniqueQuerySummary = new UniqueValue();
    for (String rawQueryText : rawQueryTexts) {
      SqlQuery sanitizedQuery = SqlQuerySanitizerUtil.sanitizeWithSummary(rawQueryText);
      uniqueStoredProcedureName.set(sanitizedQuery.getStoredProcedureName());
      uniqueQueryTexts.add(querySanitizationEnabled ? sanitizedQuery.getQueryText() : rawQueryText);
      uniqueQuerySummary.set(sanitizedQuery.getQuerySummary());
    }

    String querySummary = uniqueQuerySummary.getValue();
    return new MultiQuery(
        uniqueStoredProcedureName.getValue(),
        uniqueQueryTexts,
        querySummary == null ? "BATCH" : "BATCH " + querySummary);
  }

  @Nullable
  public String getStoredProcedureName() {
    return storedProcedureName;
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
