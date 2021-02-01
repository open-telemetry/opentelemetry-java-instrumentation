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
  private final Context parentSpan;

  public TracingMaybeObserver(final MaybeObserver<T> observer, final Context parentSpan) {
    this.observer = observer;
    this.parentSpan = parentSpan;
  }

  @Override
  public void onSubscribe(final Disposable disposable) {
    observer.onSubscribe(disposable);
  }

  @Override
  public void onSuccess(final T t) {
    try (final Scope scope = parentSpan.makeCurrent()) {
      observer.onSuccess(t);
    }
  }

  @Override
  public void onError(final Throwable throwable) {
    try (final Scope scope = parentSpan.makeCurrent()) {
      observer.onError(throwable);
    }
  }

  @Override
  public void onComplete() {
    try (final Scope scope = parentSpan.makeCurrent()) {
      observer.onComplete();
    }
  }
}
