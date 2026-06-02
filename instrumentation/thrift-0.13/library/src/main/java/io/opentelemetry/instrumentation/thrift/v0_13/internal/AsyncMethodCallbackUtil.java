/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.apache.thrift.async.AsyncMethodCallback;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class AsyncMethodCallbackUtil {

  public static <T> AsyncMethodCallback<T> wrap(
      AsyncMethodCallback<T> callback, ClientCallContext clientCallContext) {
    Context context = Context.current();
    return new AsyncMethodCallback<T>() {

      @Override
      public void onComplete(T response) {
        clientCallContext.endSpan(null);
        try (Scope ignore = context.makeCurrent()) {
          callback.onComplete(response);
        }
      }

      @Override
      public void onError(Exception exception) {
        clientCallContext.endSpan(exception);
        try (Scope ignore = context.makeCurrent()) {
          callback.onError(exception);
        }
      }
    };
  }

  private AsyncMethodCallbackUtil() {}
}
