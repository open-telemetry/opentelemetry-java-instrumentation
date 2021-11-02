/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.GenericProgressiveFutureListener;
import io.netty.util.concurrent.ProgressiveFuture;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.caching.Cache;

public final class FutureListenerWrappers {
  // Instead of VirtualField use Cache with weak keys and weak values to store link between original
  // listener and wrapper. VirtualField works fine when wrapper is stored in a field on original
  // listener, but when listener class is a lambda instead of field it gets stored in a map with
  // weak keys where original listener is key and wrapper is value. As wrapper has a strong
  // reference to original listener this causes a memory leak.
  // Also note that it's ok if the value is collected prior to the key, since this cache is only
  // used to remove the wrapped listener from the netty future, and if the value is collected prior
  // to the key, that means it's no longer used (referenced) by the netty future anyways.
  private static final Cache<
          GenericFutureListener<? extends Future<?>>, GenericFutureListener<? extends Future<?>>>
      wrappers = Cache.builder().setWeakKeys().setWeakValues().build();

  private static final ClassValue<Boolean> shouldWrap =
      new ClassValue<Boolean>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
          // we only want to wrap user callbacks
          String className = type.getName();
          return !className.startsWith("io.opentelemetry.javaagent.")
              && !className.startsWith("io.netty.");
        }
      };

  public static boolean shouldWrap(GenericFutureListener<? extends Future<?>> listener) {
    return shouldWrap.get(listener.getClass());
  }

  @SuppressWarnings("unchecked")
  public static GenericFutureListener<?> wrap(
      Context context, GenericFutureListener<? extends Future<?>> delegate) {
    return wrappers.computeIfAbsent(
        delegate,
        key -> {
          if (delegate instanceof GenericProgressiveFutureListener) {
            return new WrappedProgressiveFutureListener(
                context, (GenericProgressiveFutureListener<ProgressiveFuture<?>>) delegate);
          } else {
            return new WrappedFutureListener(context, (GenericFutureListener<Future<?>>) delegate);
          }
        });
  }

  public static GenericFutureListener<? extends Future<?>> getWrapper(
      GenericFutureListener<? extends Future<?>> delegate) {
    GenericFutureListener<? extends Future<?>> wrapper = wrappers.get(delegate);
    return wrapper == null ? delegate : wrapper;
  }

  private static final class WrappedFutureListener implements GenericFutureListener<Future<?>> {

    private final Context context;
    private final GenericFutureListener<Future<?>> delegate;

    private WrappedFutureListener(Context context, GenericFutureListener<Future<?>> delegate) {
      this.context = context;
      this.delegate = delegate;
    }

    @Override
    public void operationComplete(Future<?> future) throws Exception {
      try (Scope ignored = context.makeCurrent()) {
        delegate.operationComplete(future);
      }
    }
  }

  private static final class WrappedProgressiveFutureListener
      implements GenericProgressiveFutureListener<ProgressiveFuture<?>> {

    private final Context context;
    private final GenericProgressiveFutureListener<ProgressiveFuture<?>> delegate;

    private WrappedProgressiveFutureListener(
        Context context, GenericProgressiveFutureListener<ProgressiveFuture<?>> delegate) {
      this.context = context;
      this.delegate = delegate;
    }

    @Override
    public void operationProgressed(ProgressiveFuture<?> progressiveFuture, long l, long l1)
        throws Exception {
      try (Scope ignored = context.makeCurrent()) {
        delegate.operationProgressed(progressiveFuture, l, l1);
      }
    }

    @Override
    public void operationComplete(ProgressiveFuture<?> progressiveFuture) throws Exception {
      try (Scope ignored = context.makeCurrent()) {
        delegate.operationComplete(progressiveFuture);
      }
    }
  }

  private FutureListenerWrappers() {}
}
