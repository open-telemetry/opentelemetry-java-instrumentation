/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava.v2_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.reactivex.Observable;
import io.reactivex.Observer;
import java.lang.reflect.Field;
import java.util.concurrent.Callable;

/** Wraps an ObservableFromCallable to ensure context propagation for the callable execution. */
final class TracingCallableObservable<T> extends Observable<T> {
  private final Observable<T> source;
  private final Context context;

  TracingCallableObservable(Observable<T> source, Context context) {
    this.source = source;
    this.context = context;
  }

  @Override
  protected void subscribeActual(Observer<? super T> observer) {
    try {
      Field callableField = source.getClass().getDeclaredField("callable");
      callableField.setAccessible(true);
      @SuppressWarnings("unchecked")
      Callable<T> originalCallable = (Callable<T>) callableField.get(source);

      if (originalCallable != null) {
        Callable<T> wrappedCallable =
            () -> {
              try (Scope ignored = context.makeCurrent()) {
                return originalCallable.call();
              }
            };

        callableField.set(source, wrappedCallable);
      }
    } catch (Exception e) {
      // If reflection fails, fall back to original behavior
    }

    source.subscribe(observer);
  }
}
