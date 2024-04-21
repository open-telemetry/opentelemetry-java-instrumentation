/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.v20_0;

import graphql.execution.ResultPath;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.language.OperationDefinition.Operation;
import io.opentelemetry.context.Context;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
final class OpenTelemetryInstrumentationState implements InstrumentationState {
  private static final String ROOT_PATH = ResultPath.rootPath().toString();

  private final ConcurrentMap<String, Context> contextStorage = new ConcurrentHashMap<>();

  private Operation operation;
  private String operationName;
  private String query;

  public Context getContext() {
    return contextStorage.getOrDefault(ROOT_PATH, Context.current());
  }

  public void setContext(Context context) {
    this.contextStorage.put(ROOT_PATH, context);
  }

  public Operation getOperation() {
    return operation;
  }

  public void setOperation(Operation operation) {
    this.operation = operation;
  }

  public String getOperationName() {
    return operationName;
  }

  public void setOperationName(String operationName) {
    this.operationName = operationName;
  }

  public String getQuery() {
    return query;
  }

  public void setQuery(String query) {
    this.query = query;
  }

  public Context setContextForPath(ResultPath resultPath, Context context) {
    return contextStorage.putIfAbsent(resultPath.toString(), context);
  }

  public Context getParentContextForPath(ResultPath resultPath) {

    // Navigate up the path until we find the closest parent context
    for (ResultPath currentPath = resultPath.getParent();
        currentPath != null;
        currentPath = currentPath.getParent()) {

      Context parentContext = contextStorage.getOrDefault(currentPath.toString(), null);

      if (parentContext != null) {
        return parentContext;
      }
    }

    // Fallback to returning the context for ROOT_PATH
    return getContext();
  }
}
