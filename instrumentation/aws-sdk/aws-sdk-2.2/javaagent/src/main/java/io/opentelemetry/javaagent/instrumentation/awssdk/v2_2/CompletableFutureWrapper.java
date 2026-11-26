/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v2_2;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.concurrent.CompletableFuture;

public class CompletableFutureWrapper {

  private CompletableFutureWrapper() {}

  public static <T> CompletableFuture<T> wrap(CompletableFuture<T> future) {
    return wrap(future, Context.current());
  }

  public static <T> CompletableFuture<T> wrap(CompletableFuture<T> future, Context context) {
    CompletableFuture<T> result = new CompletableFuture<>();
    future.whenComplete((T value, Throwable throwable) -> {
      try (Scope ignored = context.makeCurrent()) {
        if (throwable != null) {
          result.completeExceptionally(throwable);
        } else {
          result.complete(value);
        }
      }
    });
    // The generated async clients abort an in-flight request by completing the future returned
    // from the client handler exceptionally, e.g. when the future returned to the caller is
    // cancelled. Forward exceptional completion of the wrapper to the original future so that this
    // still works with the wrapper in between.
    result.whenComplete((T value, Throwable throwable) -> {
      if (throwable != null) {
        future.completeExceptionally(throwable);
      }
    });

    return result;
  }
}
