/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17.internal;

import static java.util.logging.Level.FINE;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class CompletableFutureWrapper<T> extends CompletableFuture<T> {

  private static final Logger logger = Logger.getLogger(CompletableFutureWrapper.class.getName());

  private final CompletableFuture<?> sourceFuture;

  private CompletableFutureWrapper(CompletableFuture<?> sourceFuture) {
    this.sourceFuture = sourceFuture;
  }

  public static <T> CompletableFuture<T> wrap(
      CompletableFuture<T> future,
      Context context,
      BiConsumer<? super T, ? super Throwable> completion) {
    CompletableFuture<T> result = new CompletableFutureWrapper<>(future);
    future.whenComplete(
        (T value, Throwable throwable) -> {
          try {
            completion.accept(value, throwable);
          } catch (Throwable t) {
            logger.log(FINE, "Failed to finish NATS request instrumentation", t);
          } finally {
            try (Scope ignored = context.makeCurrent()) {
              if (throwable != null) {
                result.completeExceptionally(throwable);
              } else {
                result.complete(value);
              }
            }
          }
        });

    return result;
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    if (isDone()) {
      return isCancelled();
    }
    return sourceFuture.cancel(mayInterruptIfRunning) && isCancelled();
  }
}
