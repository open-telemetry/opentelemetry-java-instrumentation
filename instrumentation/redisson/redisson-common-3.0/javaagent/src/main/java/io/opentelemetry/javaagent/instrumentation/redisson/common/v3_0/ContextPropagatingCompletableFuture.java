/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.concurrent.CompletableFuture;

public final class ContextPropagatingCompletableFuture<T> extends CompletableFuture<T> {

  private ContextPropagatingCompletableFuture(CompletableFuture<T> delegate, Context context) {
    delegate.whenComplete(
        (result, error) -> {
          try (Scope ignored = context.makeCurrent()) {
            if (delegate.isCancelled()) {
              cancel(false);
            } else if (error != null) {
              completeExceptionally(error);
            } else {
              complete(result);
            }
          }
        });
  }

  public static <T> CompletableFuture<T> wrap(CompletableFuture<T> delegate, Context context) {
    if (context == Context.root() || delegate instanceof ContextPropagatingCompletableFuture) {
      return delegate;
    }
    return new ContextPropagatingCompletableFuture<>(delegate, context);
  }
}
