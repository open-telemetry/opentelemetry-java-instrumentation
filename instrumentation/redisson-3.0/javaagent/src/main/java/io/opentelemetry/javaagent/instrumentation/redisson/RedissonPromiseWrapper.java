/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;

public class RedissonPromiseWrapper<T> extends RedissonPromise<T> implements PromiseWrapper<T> {
  private volatile EndOperationListener<T> endOperationListener;

  private RedissonPromiseWrapper(RPromise<T> delegate, Context context) {
    this.whenComplete(
        (result, error) -> {
          EndOperationListener<T> endOperationListener = this.endOperationListener;
          if (endOperationListener != null) {
            endOperationListener.accept(result, error);
          }
          try (Scope ignored = context.makeCurrent()) {
            if (error != null) {
              delegate.tryFailure(error);
            } else {
              delegate.trySuccess(result);
            }
          }
        });
  }

  public static <T> RPromise<T> wrap(RPromise<T> delegate) {
    if (delegate instanceof RedissonPromiseWrapper) {
      return delegate;
    }

    return new RedissonPromiseWrapper<>(delegate, Context.current());
  }

  @Override
  public void setEndOperationListener(EndOperationListener<T> endOperationListener) {
    this.endOperationListener = endOperationListener;
  }
}
