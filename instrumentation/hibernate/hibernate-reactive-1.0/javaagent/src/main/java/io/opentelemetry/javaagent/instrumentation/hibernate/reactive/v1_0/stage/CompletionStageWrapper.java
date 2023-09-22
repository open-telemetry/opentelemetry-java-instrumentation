/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.reactive.v1_0.stage;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class CompletionStageWrapper {

  private CompletionStageWrapper() {}

  public static <T> CompletionStage<T> wrap(CompletionStage<T> future) {
    Context context = Context.current();
    if (context != Context.root()) {
      return wrap(future, context);
    }
    return future;
  }

  private static <T> CompletionStage<T> wrap(CompletionStage<T> completionStage, Context context) {
    CompletableFuture<T> result = new CompletableFuture<>();
    completionStage.whenComplete(
        (T value, Throwable throwable) -> {
          try (Scope ignored = context.makeCurrent()) {
            if (throwable != null) {
              result.completeExceptionally(throwable);
            } else {
              result.complete(value);
            }
          }
        });

    return result;
  }
}
