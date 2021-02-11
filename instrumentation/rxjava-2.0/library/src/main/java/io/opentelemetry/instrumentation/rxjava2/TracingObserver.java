/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava2;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.reactivex.Observer;
import io.reactivex.annotations.NonNull;
import io.reactivex.internal.fuseable.QueueDisposable;
import io.reactivex.internal.observers.BasicFuseableObserver;

public class TracingObserver<T> extends BasicFuseableObserver<T, T> {

  private final Context parentSpan;

  TracingObserver(final Observer<? super T> actual, final Context parentSpan) {
    super(actual);
    this.parentSpan = parentSpan;
  }

  @Override
  public void onNext(@NonNull T t) {
    try (Scope ignored = parentSpan.makeCurrent()) {
      actual.onNext(t);
    }
  }

  @Override
  public void onError(Throwable t) {
    try (Scope ignored = parentSpan.makeCurrent()) {
      actual.onError(t);
    }
  }

  @Override
  public void onComplete() {
    try (Scope ignored = parentSpan.makeCurrent()) {
      actual.onComplete();
    }
  }

  @Override
  public int requestFusion(int mode) {
    final QueueDisposable<T> qd = this.qs;
    if (qd != null) {
      final int m = qd.requestFusion(mode);
      sourceMode = m;
      return m;
    }
    return NONE;
  }

  @Override
  public T poll() throws Exception {
    return qs.poll();
  }
}
