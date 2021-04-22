/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.caching.Cache;

public final class WrappedFutureListener<F extends Future<? super Void>>
    implements GenericFutureListener<F> {

  private static final Cache<GenericFutureListener<?>, WrappedFutureListener<?>> wrappers =
      Cache.newBuilder().setWeakKeys().build();

  @SuppressWarnings("unchecked")
  public static <F extends Future<? super Void>> GenericFutureListener<F> wrap(
      Context context, GenericFutureListener<F> delegate) {
    if (delegate instanceof WrappedFutureListener) {
      return delegate;
    }
    return (GenericFutureListener<F>)
        wrappers.computeIfAbsent(delegate, k -> new WrappedFutureListener<>(context, delegate));
  }

  @SuppressWarnings("unchecked")
  public static <F extends Future<? super Void>> GenericFutureListener<F> getWrapper(
      GenericFutureListener<F> delegate) {
    WrappedFutureListener<F> wrapper = (WrappedFutureListener<F>) wrappers.get(delegate);
    return wrapper == null ? delegate : wrapper;
  }

  private final Context context;
  private final GenericFutureListener<F> delegate;

  private WrappedFutureListener(Context context, GenericFutureListener<F> delegate) {
    this.context = context;
    this.delegate = delegate;
  }

  @Override
  public void operationComplete(F future) throws Exception {
    try (Scope ignored = context.makeCurrent()) {
      delegate.operationComplete(future);
    }
  }
}
