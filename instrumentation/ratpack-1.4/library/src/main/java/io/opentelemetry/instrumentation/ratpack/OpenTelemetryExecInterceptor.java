/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import ratpack.exec.ExecInterceptor;
import ratpack.exec.Execution;
import ratpack.func.Block;

final class OpenTelemetryExecInterceptor implements ExecInterceptor {

  static final ExecInterceptor INSTANCE = new OpenTelemetryExecInterceptor();

  @Override
  public void intercept(Execution execution, ExecType type, Block continuation) throws Exception {
    Context otelCtx = execution.maybeGet(Context.class).orElse(null);
    if (otelCtx != null) {
      // Execution already has a context, this is an asynchronous resumption and we need to make
      // the context current.
      try (Scope ignored = otelCtx.makeCurrent()) {
        continuation.execute();
      }
      return;
    }

    try {
      continuation.execute();
    } finally {
      // An execution will start by going through OpenTelemetryHandler which opens a scope. We need
      // to close it here as this Execution is about to be unbound. It may resume later if there are
      // still asynchronous steps which will be handled above, or we may be done.
      Scope scope = execution.maybeGet(Scope.class).orElse(null);
      if (scope != null) {
        scope.close();
        execution.remove(Scope.class);
      }
    }
  }
}
