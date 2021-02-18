/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava2;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.reactivex.CompletableObserver;
import io.reactivex.disposables.Disposable;

public final class TracingCompletableObserver implements CompletableObserver {

  private final CompletableObserver observer;
  private final Context context;

  public TracingCompletableObserver(final CompletableObserver observer, final Context context) {
    this.observer = observer;
    this.context = context;
  }

  @Override
  public void onSubscribe(final Disposable disposable) {
    observer.onSubscribe(disposable);
  }

  @Override
  public void onComplete() {
    try (Scope ignored = context.makeCurrent()) {
      observer.onComplete();
    }
  }

  @Override
  public void onError(final Throwable throwable) {
    try (Scope ignored = context.makeCurrent()) {
      observer.onError(throwable);
    }
  }
}
