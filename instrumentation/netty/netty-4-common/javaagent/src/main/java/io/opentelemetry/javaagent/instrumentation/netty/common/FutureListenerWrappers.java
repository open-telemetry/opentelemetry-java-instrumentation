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
import io.opentelemetry.instrumentation.api.field.VirtualField;

public final class FutureListenerWrappers {
  // important: if this is ever converted to library instrumentation, this will create a memory leak
  // because while library implementation of VirtualField maintains a weak reference to its keys, it
  // maintains a strong reference to its values, and the wrapper has a strong reference to original
  // listener, which will create a memory leak.
  // this is not a problem in the javaagent's implementation of VirtualField, since it injects the
  // value directly into the key as a field, and so the value is only retained strongly by the key,
  // and so they can be collected together.
  @SuppressWarnings("rawtypes")
  private static final VirtualField<GenericFutureListener, GenericFutureListener> wrapperField =
      VirtualField.find(GenericFutureListener.class, GenericFutureListener.class);

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
    GenericFutureListener<? extends Future<?>> wrapper = wrapperField.get(delegate);
    if (wrapper == null) {
      if (delegate instanceof GenericProgressiveFutureListener) {
        wrapper =
            new WrappedProgressiveFutureListener(
                context, (GenericProgressiveFutureListener<ProgressiveFuture<?>>) delegate);
      } else {
        wrapper = new WrappedFutureListener(context, (GenericFutureListener<Future<?>>) delegate);
      }
      wrapperField.set(delegate, wrapper);
    }
    return wrapper;
  }

  @SuppressWarnings("unchecked")
  public static GenericFutureListener<? extends Future<?>> getWrapper(
      GenericFutureListener<? extends Future<?>> delegate) {
    GenericFutureListener<? extends Future<?>> wrapper = wrapperField.get(delegate);
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
