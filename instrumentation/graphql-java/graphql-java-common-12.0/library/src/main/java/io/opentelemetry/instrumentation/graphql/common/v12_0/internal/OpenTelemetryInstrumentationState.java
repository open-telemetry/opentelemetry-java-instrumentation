/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.common.v12_0.internal;

import graphql.execution.instrumentation.InstrumentationState;
import graphql.language.OperationDefinition.Operation;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class OpenTelemetryInstrumentationState implements InstrumentationState {
  @Nullable private Context context;
  @Nullable private Span currentSpan;
  private boolean operationSpanCreated;
  @Nullable private Operation operation;
  @Nullable private String operationName;
  @Nullable private String query;

  @Nullable
  public Context getContext() {
    return context;
  }

  public void setContext(Context context) {
    this.context = context;
  }

  /** Whether this instrumentation created its own GraphQL operation span. */
  public boolean isOperationSpanCreated() {
    return operationSpanCreated;
  }

  public void setOperationSpanCreated(boolean operationSpanCreated) {
    this.operationSpanCreated = operationSpanCreated;
  }

  /**
   * The span that was current when execution began (normally the enclosing server span), used when
   * stamping GraphQL telemetry onto it. {@code null} when there is no valid current span or when
   * current-span enrichment is disabled.
   */
  @Nullable
  public Span getCurrentSpan() {
    return currentSpan;
  }

  public void setCurrentSpan(@Nullable Span currentSpan) {
    this.currentSpan = currentSpan;
  }

  @Nullable
  public Operation getOperation() {
    return operation;
  }

  public void setOperation(Operation operation) {
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

  public void setQuery(String query) {
    this.query = query;
  }
}
