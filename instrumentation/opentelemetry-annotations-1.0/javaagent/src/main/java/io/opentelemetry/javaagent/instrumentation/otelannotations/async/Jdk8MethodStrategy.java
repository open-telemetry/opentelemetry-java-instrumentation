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
      if (endSynchronously(future, tracer, context)) {
        return future;
      }
      return endWhenComplete(future, tracer, context);
    } else if (result instanceof CompletionStage) {
      CompletionStage<?> stage = (CompletionStage<?>) result;
      return endWhenComplete(stage, tracer, context);
    }
    tracer.end(context);
    return result;
  }

  /**
   * Checks to see if the {@link CompletableFuture} has already been completed and if so
   * synchronously ends the span to avoid additional allocations and overhead registering for
   * notification of completion.
   */
  private boolean endSynchronously(
      CompletableFuture<?> future, BaseTracer tracer, Context context) {

    if (future.isDone()) {
      if (future.isCompletedExceptionally()) {
        // If the future completed exceptionally then join to catch the exception
        // so that it can be recorded to the span
        try {
          future.join();
        } catch (Exception exception) {
          tracer.endExceptionally(context, exception);
          return true;
        }
      }
      tracer.end(context);
      return true;
    } else {
      return false;
    }
  }

  /**
   * Registers for notification of the completion of the {@link CompletionStage} at which time the
   * span will be ended.
   */
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
