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

  private RedissonPromiseWrapper(RPromise<T> delegate) {
    this.whenComplete(
        (result, error) -> {
          EndOperationListener<T> endOperationListener = this.endOperationListener;
          if (endOperationListener != null) {
            endOperationListener.accept(result, error);
          }
          if (error != null) {
            delegate.tryFailure(error);
          } else {
            delegate.trySuccess(result);
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

  /**
   * Wrap {@link RPromise} to run callbacks with the context that was current at the time this
   * method was called.
   *
   * <p>This method should be called on, or as close as possible to, the {@link RPromise} that is
   * returned to the user to ensure that the callbacks added by user are run in appropriate context.
   */
  public static <T> RPromise<T> wrapContext(RPromise<T> promise) {
    Context context = Context.current();
    // when returned promise is completed, complete input promise with context that was current
    // at the time when the promise was wrapped
    RPromise<T> result = new RedissonPromise<T>();
    result.whenComplete(
        (value, error) -> {
          try (Scope ignored = context.makeCurrent()) {
            if (error != null) {
              promise.tryFailure(error);
            } else {
              promise.trySuccess(value);
            }
          }
        });

    return result;
  }

  @Override
  public void setEndOperationListener(EndOperationListener<T> endOperationListener) {
    this.endOperationListener = endOperationListener;
  }
}
