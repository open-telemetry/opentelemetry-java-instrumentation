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
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;

public final class FutureListenerWrappers {
  @SuppressWarnings("unchecked")
  public static GenericFutureListener<? extends Future<? super Void>> wrap(
      ContextStore<GenericFutureListener, GenericFutureListener> contextStore,
      Context context,
      GenericFutureListener<? extends Future<? super Void>> delegate) {
    if (delegate instanceof WrappedFutureListener
        || delegate instanceof WrappedProgressiveFutureListener) {
      return delegate;
    }
    return (GenericFutureListener<? extends Future<? super Void>>)
        contextStore.putIfAbsent(
            delegate,
            () -> {
              if (delegate instanceof GenericProgressiveFutureListener) {
                return new WrappedProgressiveFutureListener(
                    context,
                    (GenericProgressiveFutureListener<ProgressiveFuture<? super Void>>) delegate);
              } else {
                return new WrappedFutureListener(
                    context, (GenericFutureListener<Future<? super Void>>) delegate);
              }
            });
  }

  public static GenericFutureListener<? extends Future<? super Void>> getWrapper(
      ContextStore<GenericFutureListener, GenericFutureListener> contextStore,
      GenericFutureListener<? extends Future<? super Void>> delegate) {
    GenericFutureListener<? extends Future<? super Void>> wrapper =
        (GenericFutureListener<? extends Future<? super Void>>) contextStore.get(delegate);
    return wrapper == null ? delegate : wrapper;
  }

  private static final class WrappedFutureListener
      implements GenericFutureListener<Future<? super Void>> {

    private final Context context;
    private final GenericFutureListener<Future<? super Void>> delegate;

    private WrappedFutureListener(
        Context context, GenericFutureListener<Future<? super Void>> delegate) {
      this.context = context;
      this.delegate = delegate;
    }

    @Override
    public void operationComplete(Future<? super Void> future) throws Exception {
      try (Scope ignored = context.makeCurrent()) {
        delegate.operationComplete(future);
      }
    }
  }

  private static final class WrappedProgressiveFutureListener
      implements GenericProgressiveFutureListener<ProgressiveFuture<? super Void>> {

    private final Context context;
    private final GenericProgressiveFutureListener<ProgressiveFuture<? super Void>> delegate;

    private WrappedProgressiveFutureListener(
        Context context,
        GenericProgressiveFutureListener<ProgressiveFuture<? super Void>> delegate) {
      this.context = context;
      this.delegate = delegate;
    }

    @Override
    public void operationProgressed(
        ProgressiveFuture<? super Void> progressiveFuture, long l, long l1) throws Exception {
      try (Scope ignored = context.makeCurrent()) {
        delegate.operationProgressed(progressiveFuture, l, l1);
      }
    }

    @Override
    public void operationComplete(ProgressiveFuture<? super Void> progressiveFuture)
        throws Exception {
      try (Scope ignored = context.makeCurrent()) {
        delegate.operationComplete(progressiveFuture);
      }
    }
  }

  private FutureListenerWrappers() {}
}
