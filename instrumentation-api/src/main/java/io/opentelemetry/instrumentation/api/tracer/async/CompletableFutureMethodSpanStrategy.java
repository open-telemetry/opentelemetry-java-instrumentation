/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer.async;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import java.util.concurrent.CompletableFuture;

enum CompletableFutureMethodSpanStrategy implements MethodSpanStrategy {
  INSTANCE;

  @Override
  public Object end(BaseTracer tracer, Context context, Object result) {
    if (result instanceof CompletableFuture) {
      CompletableFuture<?> future = (CompletableFuture<?>) result;
      if (future.isDone()) {
        return endSynchronously(tracer, context, future);
      } else {
        return endOnCompletion(tracer, context, future);
      }
    }
    tracer.end(context);
    return result;
  }

  private CompletableFuture<?> endSynchronously(
      BaseTracer tracer, Context context, CompletableFuture<?> future) {
    try {
      future.join();
      tracer.end(context);
    } catch (Exception exception) {
      tracer.endExceptionally(context, exception);
    }
    return future;
  }

  private CompletableFuture<?> endOnCompletion(
      BaseTracer tracer, Context context, CompletableFuture<?> future) {
    return future.whenComplete(
        (value, error) -> {
          if (error != null) {
            tracer.endExceptionally(context, error);
          } else {
            tracer.end(context);
          }
        });
  }
}
