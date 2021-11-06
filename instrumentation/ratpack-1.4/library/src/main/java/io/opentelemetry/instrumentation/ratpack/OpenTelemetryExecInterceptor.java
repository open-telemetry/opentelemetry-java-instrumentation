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

public final class OpenTelemetryExecInterceptor implements ExecInterceptor {

  static final ExecInterceptor INSTANCE = new OpenTelemetryExecInterceptor();

  @Override
  public void intercept(Execution execution, ExecType type, Block continuation) throws Exception {
    Context otelCtx = execution.maybeGet(Context.class).orElse(null);
    if (otelCtx == null) {
      // There is no OTel Context yet meaning this is the beginning of an Execution, before running
      // the handler chain, which includes OpenTelemetryServerHandler. Run the chain.
      executeHandlerChainAndThenCloseScope(execution, continuation);
    } else {
      // Execution already has a context, this is an asynchronous resumption and we need to make
      // the context current.
      executeContinuationWithContext(continuation, otelCtx);
    }
  }

  private static void executeHandlerChainAndThenCloseScope(Execution execution, Block continuation)
      throws Exception {
    try {
      continuation.execute();
    } finally {
      // The handler chain, including OpenTelemetryServerHandler, has finished and we are about
      // to unbind the Execution from its thread. As such, we need to make sure to close the
      // thread-local Scope that was created by OpenTelemetryServerHandler. The Execution still
      // has an OTel Context, so if it happens to resume because the user used an asynchronous
      // flow, the interceptor will run again and instead make the context current by
      // calling executeContinuationWithContext.
      Scope scope = execution.maybeGet(Scope.class).orElse(null);
      if (scope != null) {
        scope.close();
        execution.remove(Scope.class);
      }
    }
  }

  private static void executeContinuationWithContext(Block continuation, Context otelCtx)
      throws Exception {
    try (Scope ignored = otelCtx.makeCurrent()) {
      continuation.execute();
    }
  }
}
