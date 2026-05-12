/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public final class ContextPropagatingCompletableFuture<T> extends CompletableFuture<T> {
  private final CompletableFuture<T> delegate;
  private final Context context;

  private ContextPropagatingCompletableFuture(CompletableFuture<T> delegate, Context context) {
    this.delegate = delegate;
    this.context = context;
    delegate.whenComplete(
        (result, error) -> {
          try (Scope ignored = context.makeCurrent()) {
            if (delegate.isCancelled()) {
              super.cancel(false);
            } else if (error != null) {
              super.completeExceptionally(error);
            } else {
              super.complete(result);
            }
          }
        });
  }

  public static <T> CompletableFuture<T> wrap(CompletableFuture<T> delegate, Context context) {
    if (!Span.fromContext(context).getSpanContext().isValid()
        || delegate instanceof ContextPropagatingCompletableFuture) {
      return delegate;
    }
    return new ContextPropagatingCompletableFuture<>(delegate, context);
  }

  @Override
  public CompletableFuture<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
    return super.whenComplete(
        (result, error) -> {
          if (Context.current() == context) {
            action.accept(result, error);
            return;
          }
          try (Scope ignored = context.makeCurrent()) {
            action.accept(result, error);
          }
        });
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    boolean delegateCancelled = delegate.cancel(mayInterruptIfRunning);
    if (!delegateCancelled && !delegate.isCancelled()) {
      return false;
    }
    boolean wrapperCancelled = super.cancel(mayInterruptIfRunning);
    return delegateCancelled || wrapperCancelled;
  }
}
