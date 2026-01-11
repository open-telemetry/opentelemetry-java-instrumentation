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

  @Nullable private final String target;
  @Nullable private final String operationName;
  private final Set<String> statements;

  private MultiQuery(
      @Nullable String target, @Nullable String operationName, Set<String> statements) {
    this.target = target;
    this.operationName = operationName;
    this.statements = statements;
  }

  static MultiQuery analyze(
      Collection<String> rawQueryTexts, boolean statementSanitizationEnabled) {
    UniqueValue uniqueTarget = new UniqueValue();
    UniqueValue uniqueOperationName = new UniqueValue();
    Set<String> uniqueStatements = new LinkedHashSet<>();
    for (String rawQueryText : rawQueryTexts) {
      SqlStatementInfo sanitizedStatement = SqlStatementSanitizerUtil.sanitize(rawQueryText);
      String target = sanitizedStatement.getTarget();
      uniqueTarget.set(target);
      String operationName = sanitizedStatement.getOperationName();
      uniqueOperationName.set(operationName);
      uniqueStatements.add(
          statementSanitizationEnabled ? sanitizedStatement.getQueryText() : rawQueryText);
    }

    return new MultiQuery(
        uniqueTarget.getValue(), uniqueOperationName.getValue(), uniqueStatements);
  }

  @Nullable
  public String getTarget() {
    return target;
  }

  /**
   * @deprecated Use {@link #getTarget()} instead.
   */
  @Deprecated
  @Nullable
  public String getMainIdentifier() {
    return getTarget();
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
