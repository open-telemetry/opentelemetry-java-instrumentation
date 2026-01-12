/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javahttpclient.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.concurrent.CompletableFuture;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class CompletableFutureWrapper<T> extends CompletableFuture<T> {
  private final CompletableFuture<?> future;

  private CompletableFutureWrapper(CompletableFuture<?> future) {
    this.future = future;
  }

  public static <T> CompletableFuture<T> wrap(CompletableFuture<T> future, Context context) {
    CompletableFuture<T> result = new CompletableFutureWrapper<>(future);
    future.whenComplete(
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

  @Override
  public <U> CompletableFuture<U> newIncompleteFuture() {
    return new CompletableFutureWrapper<>(future);
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    boolean result = super.cancel(mayInterruptIfRunning);
    future.cancel(mayInterruptIfRunning);
    return result;
  }
}
