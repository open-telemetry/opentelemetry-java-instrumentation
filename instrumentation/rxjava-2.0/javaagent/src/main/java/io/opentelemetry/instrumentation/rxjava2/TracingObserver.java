/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava2;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

public final class TracingObserver<T> implements Observer<T> {

  private final Observer<T> observer;
  private final Context context;

  public TracingObserver(final Observer<T> observer, final Context context) {
    this.observer = observer;
    this.context = context;
  }

  @Override
  public void onSubscribe(final Disposable disposable) {
    observer.onSubscribe(disposable);
  }

  @Override
  public void onNext(final T t) {
    try (Scope ignored = context.makeCurrent()) {
      observer.onNext(t);
    }
  }

  @Override
  public void onError(final Throwable throwable) {
    try (Scope ignored = context.makeCurrent()) {
      observer.onError(throwable);
    }
  }

  @Override
  public void onComplete() {
    try (final Scope scope = context.makeCurrent()) {
      observer.onComplete();
    }
  }
}
