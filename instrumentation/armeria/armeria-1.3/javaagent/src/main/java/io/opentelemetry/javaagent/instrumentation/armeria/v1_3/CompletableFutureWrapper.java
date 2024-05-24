/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.armeria.v1_3;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.concurrent.CompletableFuture;

public final class CompletableFutureWrapper {

  private CompletableFutureWrapper() {}

  public static <T> CompletableFuture<T> wrap(CompletableFuture<T> future) {
    if (future == null) {
      return null;
    }
    Context context = Context.current();
    if (context != Context.root()) {
      return wrap(future, context);
    }
    return future;
  }

  private static <T> CompletableFuture<T> wrap(CompletableFuture<T> future, Context context) {
    CompletableFuture<T> result = new CompletableFuture<>();
    result.whenComplete(
        (T value, Throwable throwable) -> {
          try (Scope ignored = context.makeCurrent()) {
            if (throwable != null) {
              future.completeExceptionally(throwable);
            } else {
              future.complete(value);
            }
          }
        });

    return result;
  }
}
