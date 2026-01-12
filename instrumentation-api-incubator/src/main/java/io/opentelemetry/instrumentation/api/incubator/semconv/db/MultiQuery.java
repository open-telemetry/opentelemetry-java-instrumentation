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

  @Nullable private final String mainIdentifier;
  @Nullable private final String operation;
  private final Set<String> statements;

  private MultiQuery(
      @Nullable String mainIdentifier, @Nullable String operation, Set<String> statements) {
    this.mainIdentifier = mainIdentifier;
    this.operation = operation;
    this.statements = statements;
  }

  static MultiQuery analyze(
      Collection<String> rawQueryTexts, boolean statementSanitizationEnabled) {
    UniqueValue uniqueMainIdentifier = new UniqueValue();
    UniqueValue uniqueOperation = new UniqueValue();
    Set<String> uniqueStatements = new LinkedHashSet<>();
    for (String rawQueryText : rawQueryTexts) {
      SqlStatementInfo sanitizedStatement = SqlStatementSanitizerUtil.sanitize(rawQueryText);
      String mainIdentifier = sanitizedStatement.getMainIdentifier();
      uniqueMainIdentifier.set(mainIdentifier);
      String operation = sanitizedStatement.getOperation();
      uniqueOperation.set(operation);
      uniqueStatements.add(
          statementSanitizationEnabled ? sanitizedStatement.getFullStatement() : rawQueryText);
    }

    return new MultiQuery(
        uniqueMainIdentifier.getValue(), uniqueOperation.getValue(), uniqueStatements);
  }

  @Nullable
  public String getMainIdentifier() {
    return mainIdentifier;
  }

  @Nullable
  public String getOperation() {
    return operation;
  }

  public Set<String> getStatements() {
    return statements;
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
