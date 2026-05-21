/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.v3_0;

import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;
import org.redisson.api.RFuture;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;

public class ContextPropagatingRFuture<T> extends RedissonPromise<T> {

  private final RFuture<T> delegate;
  private final Context context;
  private final Promise<T> listenerPromise = ImmediateEventExecutor.INSTANCE.newPromise();
  private final Map<FutureListener<? super T>, Queue<FutureListener<T>>> listenerWrappers =
      new ConcurrentHashMap<>();

  private ContextPropagatingRFuture(RFuture<T> delegate, Context context) {
    this.delegate = delegate;
    this.context = context;
    delegate.whenComplete(
        (result, error) -> {
          try (Scope ignored = context.makeCurrent()) {
            if (delegate.isCancelled()) {
              super.cancel(false);
              listenerPromise.cancel(false);
            } else if (error != null) {
              super.tryFailure(error);
              listenerPromise.tryFailure(error);
            } else {
              super.trySuccess(result);
              listenerPromise.trySuccess(result);
            }
            listenerWrappers.clear();
          }
        });
  }

  public static <T> RFuture<T> wrap(RFuture<T> delegate, Context context) {
    if (!Span.fromContext(context).getSpanContext().isValid()
        || delegate instanceof ContextPropagatingRFuture) {
      return delegate;
    }
    return new ContextPropagatingRFuture<>(delegate, context);
  }

  @Override
  @SuppressWarnings({"rawtypes", "unchecked"}) // Redisson exposes raw listener bridge methods
  public RPromise<T> addListener(FutureListener<? super T> listener) {
    FutureListener<T> listenerWrapper = wrapListener(listener);
    if (!listenerPromise.isDone()) {
      listenerWrappers
          .computeIfAbsent(listener, ignored -> new ConcurrentLinkedQueue<>())
          .add(listenerWrapper);
    }
    listenerPromise.addListener(listenerWrapper);
    if (listenerPromise.isDone()) {
      removeTrackedListenerWrapper(listener, listenerWrapper);
    }
    return this;
  }

  @Override
  @SuppressWarnings("unchecked") // varargs override matches Redisson's generic listener API
  public RPromise<T> addListeners(FutureListener<? super T>... listeners) {
    for (FutureListener<? super T> listener : listeners) {
      addListener(listener);
    }
    return this;
  }

  @Override
  public RPromise<T> removeListener(FutureListener<? super T> listener) {
    FutureListener<T> listenerWrapper = removeTrackedListenerWrapper(listener);
    if (listenerWrapper != null) {
      listenerPromise.removeListener(listenerWrapper);
    }
    return this;
  }

  @Override
  @SuppressWarnings("unchecked") // varargs override matches Redisson's generic listener API
  public RPromise<T> removeListeners(FutureListener<? super T>... listeners) {
    for (FutureListener<? super T> listener : listeners) {
      removeListener(listener);
    }
    return this;
  }

  public void onComplete(BiConsumer<? super T, ? super Throwable> action) {
    whenComplete(action);
  }

  public boolean hasListeners() {
    return (!isDone() && listenerWrappers.values().stream().anyMatch(queue -> !queue.isEmpty()))
        || getNumberOfDependents() > 0;
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
  public synchronized boolean cancel(boolean mayInterruptIfRunning) {
    boolean delegateCancelled = delegate.cancel(mayInterruptIfRunning);
    if (!delegateCancelled && !delegate.isCancelled()) {
      return false;
    }
    boolean wrapperCancelled = super.cancel(mayInterruptIfRunning);
    listenerPromise.cancel(mayInterruptIfRunning);
    listenerWrappers.clear();
    return delegateCancelled || wrapperCancelled;
  }

  @SuppressWarnings({
    "rawtypes",
    "unchecked"
  }) // wrapper must call the user listener through Netty's raw FutureListener type
  private FutureListener<T> wrapListener(FutureListener<? super T> listener) {
    return future -> {
      if (Context.current() == context) {
        ((FutureListener) listener).operationComplete(future);
        return;
      }
      try (Scope ignored = context.makeCurrent()) {
        ((FutureListener) listener).operationComplete(future);
      }
    };
  }

  private FutureListener<T> removeTrackedListenerWrapper(FutureListener<? super T> listener) {
    Queue<FutureListener<T>> listenerWrapperQueue = listenerWrappers.get(listener);
    if (listenerWrapperQueue == null) {
      return null;
    }
    FutureListener<T> listenerWrapper = listenerWrapperQueue.poll();
    if (listenerWrapperQueue.isEmpty()) {
      listenerWrappers.remove(listener, listenerWrapperQueue);
    }
    return listenerWrapper;
  }

  private void removeTrackedListenerWrapper(
      FutureListener<? super T> listener, FutureListener<T> listenerWrapper) {
    Queue<FutureListener<T>> listenerWrapperQueue = listenerWrappers.get(listener);
    if (listenerWrapperQueue == null) {
      return;
    }
    listenerWrapperQueue.remove(listenerWrapper);
    if (listenerWrapperQueue.isEmpty()) {
      listenerWrappers.remove(listener, listenerWrapperQueue);
    }
  }
}
