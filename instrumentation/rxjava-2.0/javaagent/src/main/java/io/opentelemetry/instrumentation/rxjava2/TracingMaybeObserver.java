/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava2;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.reactivex.MaybeObserver;
import io.reactivex.disposables.Disposable;

public final class TracingMaybeObserver<T> implements MaybeObserver<T> {

  private final MaybeObserver<T> observer;
  private final Context context;

  public TracingMaybeObserver(final MaybeObserver<T> observer, final Context context) {
    this.observer = observer;
    this.context = context;
  }

  @Override
  public void onSubscribe(final Disposable disposable) {
    observer.onSubscribe(disposable);
  }

  @Override
  public void onSuccess(final T t) {
    try (Scope ignored = context.makeCurrent()) {
      observer.onSuccess(t);
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
    try (Scope ignored = context.makeCurrent()) {
      observer.onComplete();
    }
  }
}
