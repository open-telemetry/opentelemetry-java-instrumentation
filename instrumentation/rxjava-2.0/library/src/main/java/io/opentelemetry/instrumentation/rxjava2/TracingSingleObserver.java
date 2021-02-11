/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava2;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.reactivex.SingleObserver;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.internal.disposables.DisposableHelper;

class TracingSingleObserver<T> implements SingleObserver<T>, Disposable {

  private final SingleObserver<T> actual;
  private final Context parentSpan;
  private Disposable disposable;

  TracingSingleObserver(final SingleObserver<T> actual, final Context parentSpan) {
    this.actual = actual;
    this.parentSpan = parentSpan;
  }

  @Override
  public void onSubscribe(final @NonNull Disposable d) {
    if (!DisposableHelper.validate(disposable, d)) {
      return;
    }
    this.disposable = d;
    actual.onSubscribe(this);
  }

  @Override
  public void onSuccess(final @NonNull T t) {
    try (Scope ignored = parentSpan.makeCurrent()) {
      actual.onSuccess(t);
    }
  }

  @Override
  public void onError(@NonNull Throwable throwable) {
    try (Scope ignored = parentSpan.makeCurrent()) {
      actual.onError(throwable);
    }
  }

  @Override
  public void dispose() {
    disposable.dispose();
  }

  @Override
  public boolean isDisposed() {
    return disposable.isDisposed();
  }
}
