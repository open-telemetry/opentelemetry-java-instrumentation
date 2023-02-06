/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.concurrent.CompletableFuture;

public class CompletableFutureWrapper<T> extends CompletableFuture<T> implements PromiseWrapper<T> {
  private static final Class<?> batchPromiseClass = getBatchPromiseClass();
  private volatile EndOperationListener<T> endOperationListener;

  private CompletableFutureWrapper(CompletableFuture<T> delegate) {
    Context context = Context.current();
    this.whenComplete(
        (result, error) -> {
          EndOperationListener<T> endOperationListener = this.endOperationListener;
          if (endOperationListener != null) {
            endOperationListener.accept(result, error);
          }
          try (Scope ignored = context.makeCurrent()) {
            if (error != null) {
              delegate.completeExceptionally(error);
            } else {
              delegate.complete(result);
            }
          }
        });
  }

  /**
   * Wrap {@link CompletableFuture} so that {@link EndOperationListener}, that is used to end the
   * span, could be attached to it.
   */
  public static <T> CompletableFuture<T> wrap(CompletableFuture<T> delegate) {
    if (delegate instanceof CompletableFutureWrapper
        || (batchPromiseClass != null && batchPromiseClass.isInstance(delegate))) {
      return delegate;
    }

    return new CompletableFutureWrapper<>(delegate);
  }

  @Override
  public void setEndOperationListener(EndOperationListener<T> endOperationListener) {
    this.endOperationListener = endOperationListener;
  }

  private static Class<?> getBatchPromiseClass() {
    try {
      // using Class.forName because this class is not available in the redisson version we compile
      // against
      return Class.forName("org.redisson.command.BatchPromise");
    } catch (ClassNotFoundException exception) {
      return null;
    }
  }
}
