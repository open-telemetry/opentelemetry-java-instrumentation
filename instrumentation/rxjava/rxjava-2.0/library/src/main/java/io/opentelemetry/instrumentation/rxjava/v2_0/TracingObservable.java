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
    System.out.println("TracingObservable created with context: " + context);
  }

  @Override
  protected void subscribeActual(Observer<? super T> observer) {
    System.out.println("TracingObservable.subscribeActual called with context: " + context);
    System.out.println("Current context before making current: " + Context.current());

    try (Scope ignored = context.makeCurrent()) {
      System.out.println("Current context after making current: " + Context.current());

      // Don't double-wrap if the observer is already a TracingObserver
      if (observer instanceof TracingObserver) {
        System.out.println("Observer is already TracingObserver, not wrapping");
        source.subscribe(observer);
      } else {
        System.out.println("Wrapping observer in TracingObserver");
        source.subscribe(new TracingObserver<>(observer, context));
      }
    }
  }
}
