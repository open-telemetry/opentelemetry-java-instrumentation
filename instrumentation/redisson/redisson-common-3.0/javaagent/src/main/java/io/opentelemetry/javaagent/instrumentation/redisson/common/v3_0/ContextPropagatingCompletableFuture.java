/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

public final class ContextPropagatingCompletableFuture<T> extends CompletableFuture<T> {

  private final CompletableFuture<T> delegate;

  private ContextPropagatingCompletableFuture(CompletableFuture<T> delegate, Context context) {
    this.delegate = delegate;
    delegate.whenComplete(
        (result, error) -> {
          try (Scope ignored = context.makeCurrent()) {
            if (delegate.isCancelled()) {
              cancelFromDelegate();
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

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    // Cancel the delegate first so that it remains the source of truth for connection acquisition.
    if (!delegate.cancel(mayInterruptIfRunning)) {
      return false;
    }
    cancelFromDelegate();
    return true;
  }

  @Override
  public boolean completeExceptionally(Throwable error) {
    if (error instanceof CancellationException) {
      // Redisson 3.26+ cancels connection acquisition by completing the future exceptionally.
      if (!delegate.completeExceptionally(error)) {
        return false;
      }
      super.completeExceptionally(error);
      return true;
    }
    return super.completeExceptionally(error);
  }

  private boolean cancelFromDelegate() {
    return super.cancel(false);
  }
}
