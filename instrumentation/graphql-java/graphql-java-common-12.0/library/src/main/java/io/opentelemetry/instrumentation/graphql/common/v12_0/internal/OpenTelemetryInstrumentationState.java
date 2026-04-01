/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.common.v12_0.internal;

import graphql.execution.instrumentation.InstrumentationState;
import graphql.language.OperationDefinition.Operation;
import io.opentelemetry.context.Context;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class OpenTelemetryInstrumentationState implements InstrumentationState {
  @Nullable private Context context;
  @Nullable private Operation operation;
  @Nullable private String operationName;
  @Nullable private String query;

  @Nullable
  public Context getContext() {
    return context;
  }

  public void setContext(@Nullable Context context) {
    this.context = context;
  }

  @Nullable
  public Operation getOperation() {
    return operation;
  }

  public void setOperation(@Nullable Operation operation) {
    this.operation = operation;
  }

  @Nullable
  public String getOperationName() {
    return operationName;
  }

  public void setOperationName(@Nullable String operationName) {
    this.operationName = operationName;
  }

  @Nullable
  public String getQuery() {
    return query;
  }

  public void setQuery(@Nullable String query) {
    this.query = query;
  }
}
