/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava2;

import io.opentelemetry.context.Context;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Observer;

public class TracingObservable<T> extends Observable<T> {

  private final ObservableSource<T> source;
  private final Context parentSpan;

  public TracingObservable(final ObservableSource<T> source, final Context parentSpan) {
    this.source = source;
    this.parentSpan = parentSpan;
  }

  @Override
  protected void subscribeActual(Observer<? super T> observer) {
    source.subscribe(new TracingObserver<>(observer, parentSpan));
  }
}
