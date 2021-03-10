/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.otelannotations.async;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

enum Jdk8MethodStrategy implements MethodSpanStrategy {
  INSTANCE;

  @Override
  public boolean supports(Class<?> returnType) {
    return returnType == CompletionStage.class || returnType == CompletableFuture.class;
  }

  @Override
  public Object end(BaseTracer tracer, Context context, Class<?> returnType, Object result) {
    if (result instanceof CompletableFuture) {
      CompletableFuture<?> future = (CompletableFuture<?>) result;
      if (future.isDone()) {
        return endSynchronously(future, tracer, context);
      }
      return endWhenComplete(future, tracer, context);
    } else if (result instanceof CompletionStage) {
      CompletionStage<?> stage = (CompletionStage<?>) result;
      return endWhenComplete(stage, tracer, context);
    }
    tracer.end(context);
    return result;
  }

  private CompletableFuture<?> endSynchronously(
      CompletableFuture<?> future, BaseTracer tracer, Context context) {
    try {
      future.join();
      tracer.end(context);
    } catch (Exception exception) {
      tracer.endExceptionally(context, exception);
    }
    return future;
  }

  private CompletionStage<?> endWhenComplete(
      CompletionStage<?> stage, BaseTracer tracer, Context context) {
    return stage.whenComplete(
        (result, exception) -> {
          if (exception != null) {
            tracer.endExceptionally(context, exception);
          } else {
            tracer.end(context);
          }
        });
  }
}
