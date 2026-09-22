/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17.internal;

import static java.util.logging.Level.FINE;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class CompletableFutureWrapper<T> extends CompletableFuture<T> {

  private static final Logger logger = Logger.getLogger(CompletableFutureWrapper.class.getName());

  private final CompletableFuture<?> sourceFuture;
  private final CountDownLatch completionFinished = new CountDownLatch(1);
  private final Object completionLock = new Object();
  private CompletionState completionState = CompletionState.OPEN;

  private CompletableFutureWrapper(CompletableFuture<?> sourceFuture) {
    this.sourceFuture = sourceFuture;
  }

  public static <T> CompletableFuture<T> wrap(
      CompletableFuture<T> future,
      Context context,
      BiConsumer<? super T, ? super Throwable> completion) {
    CompletableFutureWrapper<T> result = new CompletableFutureWrapper<>(future);
    future.whenComplete(
        (T value, Throwable throwable) -> {
          try {
            completion.accept(value, throwable);
          } catch (Throwable t) {
            logger.log(FINE, "Failed to finish NATS request instrumentation", t);
          } finally {
            try {
              try (Scope ignored = context.makeCurrent()) {
                result.completeFromSource(value, throwable);
              }
            } finally {
              result.completionFinished.countDown();
            }
          }
        });

    return result;
  }

  @Override
  public boolean complete(T value) {
    if (!claimCompletion()) {
      return false;
    }
    return super.complete(value);
  }

  @Override
  public boolean completeExceptionally(Throwable throwable) {
    if (!claimCompletion()) {
      return false;
    }
    return super.completeExceptionally(throwable);
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    synchronized (completionLock) {
      if (completionState != CompletionState.OPEN) {
        return isCancelled();
      }
      completionState = CompletionState.CANCELLATION;
    }
    if (!sourceFuture.cancel(mayInterruptIfRunning)) {
      synchronized (completionLock) {
        if (completionState == CompletionState.CANCELLATION) {
          completionState = CompletionState.OPEN;
        }
      }
      return false;
    }
    awaitCompletion();
    return isCancelled();
  }

  private boolean claimCompletion() {
    synchronized (completionLock) {
      if (completionState != CompletionState.OPEN) {
        return false;
      }
      completionState = CompletionState.COMPLETION;
      return true;
    }
  }

  private void completeFromSource(T value, Throwable throwable) {
    synchronized (completionLock) {
      completionState = CompletionState.COMPLETION;
    }
    if (throwable != null) {
      super.completeExceptionally(throwable);
    } else {
      super.complete(value);
    }
  }

  private void awaitCompletion() {
    boolean interrupted = false;
    while (true) {
      try {
        completionFinished.await();
        break;
      } catch (InterruptedException e) {
        interrupted = true;
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  private enum CompletionState {
    OPEN,
    COMPLETION,
    CANCELLATION
  }
}
