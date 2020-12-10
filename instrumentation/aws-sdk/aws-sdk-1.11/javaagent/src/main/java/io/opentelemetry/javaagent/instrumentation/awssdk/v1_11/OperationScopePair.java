/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11;

import com.amazonaws.Response;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.HttpClientOperation;

public class OperationScopePair {
  private final HttpClientOperation<Response<?>> operation;
  private final Scope scope;

  public OperationScopePair(HttpClientOperation<Response<?>> operation, Scope scope) {
    this.operation = operation;
    this.scope = scope;
  }

  public HttpClientOperation<Response<?>> getOperation() {
    return operation;
  }

  public void closeScope() {
    scope.close();
  }
}
