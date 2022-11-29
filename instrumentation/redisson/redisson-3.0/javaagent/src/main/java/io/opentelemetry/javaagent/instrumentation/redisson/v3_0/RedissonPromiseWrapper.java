/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.v3_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.redisson.EndOperationListener;
import io.opentelemetry.javaagent.instrumentation.redisson.PromiseWrapper;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;

public class RedissonPromiseWrapper<T> extends RedissonPromise<T> implements PromiseWrapper<T> {
  private volatile EndOperationListener<T> endOperationListener;

  private RedissonPromiseWrapper(RPromise<T> delegate) {
    Context context = Context.current();
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

  /**
   * Wrap {@link RPromise} so that {@link EndOperationListener}, that is used to end the span, could
   * be attached to it.
   */
  public static <T> RPromise<T> wrap(RPromise<T> delegate) {
    if (delegate instanceof RedissonPromiseWrapper) {
      return delegate;
    }

    return new RedissonPromiseWrapper<>(delegate);
  }

  @Override
  public void setEndOperationListener(EndOperationListener<T> endOperationListener) {
    this.endOperationListener = endOperationListener;
  }
}
