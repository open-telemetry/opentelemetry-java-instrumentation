/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.asyncannotationsupport;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

enum Jdk8AsyncOperationEndStrategy implements AsyncOperationEndStrategy {
  INSTANCE;

  @Override
  public boolean supports(Class<?> asyncType) {
    return asyncType == CompletionStage.class || asyncType == CompletableFuture.class;
  }

  @Override
  public <REQUEST> Object end(
      Instrumenter<REQUEST, ?> instrumenter, Context context, REQUEST request, Object asyncValue) {
    if (asyncValue instanceof CompletableFuture) {
      CompletableFuture<?> future = (CompletableFuture<?>) asyncValue;
      if (tryToEndSynchronously(instrumenter, context, request, future)) {
        return future;
      }
      return endWhenComplete(instrumenter, context, request, future);
    }
    CompletionStage<?> stage = (CompletionStage<?>) asyncValue;
    return endWhenComplete(instrumenter, context, request, stage);
  }

  /**
   * Checks to see if the {@link CompletableFuture} has already been completed and if so
   * synchronously ends the span to avoid additional allocations and overhead registering for
   * notification of completion.
   */
  private static <REQUEST> boolean tryToEndSynchronously(
      Instrumenter<REQUEST, ?> instrumenter,
      Context context,
      REQUEST request,
      CompletableFuture<?> future) {

    if (!future.isDone()) {
      return false;
    }

    if (future.isCompletedExceptionally()) {
      // If the future completed exceptionally then join to catch the exception
      // so that it can be recorded to the span
      try {
        future.join();
      } catch (Throwable t) {
        instrumenter.end(context, request, null, t);
        return true;
      }
    }
    instrumenter.end(context, request, null, null);
    return true;
  }

  /**
   * Registers for notification of the completion of the {@link CompletionStage} at which time the
   * span will be ended.
   */
  private static <REQUEST> CompletionStage<?> endWhenComplete(
      Instrumenter<REQUEST, ?> instrumenter,
      Context context,
      REQUEST request,
      CompletionStage<?> stage) {
    return stage.whenComplete(
        (result, exception) -> instrumenter.end(context, request, null, exception));
  }
}
