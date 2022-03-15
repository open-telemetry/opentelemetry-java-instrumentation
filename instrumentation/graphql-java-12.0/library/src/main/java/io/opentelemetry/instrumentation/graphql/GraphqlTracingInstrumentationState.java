/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql;

import graphql.execution.instrumentation.InstrumentationState;
import graphql.language.OperationDefinition.Operation;
import io.opentelemetry.context.Context;

class GraphqlTracingInstrumentationState implements InstrumentationState {
  private Context context;
  private Operation operation;
  private String operationName;
  private String query;

  public Context getContext() {
    return context;
  }

  public void setContext(Context context) {
    this.context = context;
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
}
