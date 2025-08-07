/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava.v2_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.reactivex.Observable;
import io.reactivex.Observer;

final class TracingObservable<T> extends Observable<T> {
  private final Observable<T> source;
  private final Context context;

  TracingObservable(Observable<T> source, Context context) {
    this.source = source;
    this.context = context;
  }

  @Override
  protected void subscribeActual(Observer<? super T> observer) {
    try (Scope ignored = context.makeCurrent()) {
      // Don't double-wrap if already a TracingObserver
      if (observer instanceof TracingObserver) {
        source.subscribe(observer);
      } else {
        source.subscribe(new TracingObserver<>(observer, context));
      }
    }
  }
}
