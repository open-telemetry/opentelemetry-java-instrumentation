/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotation.support.async;

import static io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationEndSupport.tryToGetResponse;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public enum Jdk8AsyncOperationEndStrategy implements AsyncOperationEndStrategy {
  INSTANCE;

  @Override
  public boolean supports(Class<?> asyncType) {
    return asyncType == CompletionStage.class || asyncType == CompletableFuture.class;
  }

  @Override
  public <REQUEST, RESPONSE> Object end(
      Instrumenter<REQUEST, RESPONSE> instrumenter,
      Context context,
      REQUEST request,
      Object asyncValue,
      Class<RESPONSE> responseType) {
    if (asyncValue instanceof CompletableFuture) {
      CompletableFuture<?> future = (CompletableFuture<?>) asyncValue;
      if (tryToEndSynchronously(instrumenter, context, request, future, responseType)) {
        return future;
      }
      return endWhenComplete(instrumenter, context, request, future, responseType);
    }
    CompletionStage<?> stage = (CompletionStage<?>) asyncValue;
    return endWhenComplete(instrumenter, context, request, stage, responseType);
  }

  /**
   * Checks to see if the {@link CompletableFuture} has already been completed and if so
   * synchronously ends the span to avoid additional allocations and overhead registering for
   * notification of completion.
   */
  private static <REQUEST, RESPONSE> boolean tryToEndSynchronously(
      Instrumenter<REQUEST, RESPONSE> instrumenter,
      Context context,
      REQUEST request,
      CompletableFuture<?> future,
      Class<RESPONSE> responseType) {

    if (!future.isDone()) {
      return false;
    }

    try {
      Object potentialResponse = future.join();
      instrumenter.end(context, request, tryToGetResponse(responseType, potentialResponse), null);
    } catch (Throwable t) {
      instrumenter.end(context, request, null, t);
    }
    return true;
  }

  /**
   * Registers for notification of the completion of the {@link CompletionStage} at which time the
   * span will be ended.
   */
  private static <REQUEST, RESPONSE> CompletionStage<?> endWhenComplete(
      Instrumenter<REQUEST, RESPONSE> instrumenter,
      Context context,
      REQUEST request,
      CompletionStage<?> stage,
      Class<RESPONSE> responseType) {
    return stage.whenComplete(
        (result, exception) ->
            instrumenter.end(context, request, tryToGetResponse(responseType, result), exception));
  }
}
