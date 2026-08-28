/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.v3_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.redisson.api.RFuture;
import org.redisson.misc.RedissonPromise;

public class ContextPropagatingRFuture<T> extends RedissonPromise<T> {

  private final RFuture<T> delegate;

  private ContextPropagatingRFuture(RFuture<T> delegate, Context context) {
    this.delegate = delegate;
    delegate.whenComplete(
        (result, error) -> {
          try (Scope ignored = context.makeCurrent()) {
            if (delegate.isCancelled()) {
              cancelFromDelegate();
            } else if (error != null) {
              tryFailure(error);
            } else {
              trySuccess(result);
            }
          }
        });
  }

  public static <T> RFuture<T> wrap(RFuture<T> delegate, Context context) {
    if (context == Context.root() || delegate instanceof ContextPropagatingRFuture) {
      return delegate;
    }
    return new ContextPropagatingRFuture<>(delegate, context);
  }

  @SuppressWarnings("UnsynchronizedOverridesSynchronized")
  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    if (!delegate.cancel(mayInterruptIfRunning)) {
      return false;
    }
    cancelFromDelegate();
    return true;
  }

  private boolean cancelFromDelegate() {
    return super.cancel(false);
  }
}
