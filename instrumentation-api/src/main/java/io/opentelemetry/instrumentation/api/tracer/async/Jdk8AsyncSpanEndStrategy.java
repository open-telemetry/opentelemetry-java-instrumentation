/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer.async;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

enum Jdk8AsyncSpanEndStrategy implements AsyncSpanEndStrategy {
  INSTANCE;

  @Override
  public boolean supports(Class<?> returnType) {
    return returnType == CompletionStage.class || returnType == CompletableFuture.class;
  }

  @Override
  public Object end(BaseTracer tracer, Context context, Object returnValue) {
    if (returnValue instanceof CompletableFuture) {
      CompletableFuture<?> future = (CompletableFuture<?>) returnValue;
      if (endSynchronously(future, tracer, context)) {
        return future;
      }
      return endWhenComplete(future, tracer, context);
    }
    CompletionStage<?> stage = (CompletionStage<?>) returnValue;
    return endWhenComplete(stage, tracer, context);
  }

  /**
   * Checks to see if the {@link CompletableFuture} has already been completed and if so
   * synchronously ends the span to avoid additional allocations and overhead registering for
   * notification of completion.
   */
  private static boolean endSynchronously(
      CompletableFuture<?> future, BaseTracer tracer, Context context) {

    if (!future.isDone()) {
      return false;
    }

    if (future.isCompletedExceptionally()) {
      // If the future completed exceptionally then join to catch the exception
      // so that it can be recorded to the span
      try {
        future.join();
      } catch (Throwable t) {
        tracer.endExceptionally(context, t);
        return true;
      }
    }
    tracer.end(context);
    return true;
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
