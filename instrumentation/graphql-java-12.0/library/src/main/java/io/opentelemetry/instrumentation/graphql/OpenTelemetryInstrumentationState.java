/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql;

import graphql.execution.instrumentation.InstrumentationState;
import graphql.language.OperationDefinition.Operation;
import io.opentelemetry.context.Context;

final class OpenTelemetryInstrumentationState implements InstrumentationState {
  private Context context;
  private Operation operation;
  private String operationName;
  private String query;

  Context getContext() {
    return context;
  }

  void setContext(Context context) {
    this.context = context;
  }

  Operation getOperation() {
    return operation;
  }

  void setOperation(Operation operation) {
    this.operation = operation;
  }

  String getOperationName() {
    return operationName;
  }

  void setOperationName(String operationName) {
    this.operationName = operationName;
  }

  String getQuery() {
    return query;
  }

  void setQuery(String query) {
    this.query = query;
  }
}
