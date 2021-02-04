/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava2;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.reactivex.Observer;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.observables.ConnectableObservable;

public class TracingConnectableObservable<T> extends ConnectableObservable<T> {

  private final ConnectableObservable<T> source;
  private final Context parentSpan;

  public TracingConnectableObservable(
      final ConnectableObservable<T> source, final Context parentSpan) {
    this.source = source;
    this.parentSpan = parentSpan;
  }

  @Override
  public void connect(final @NonNull Consumer<? super Disposable> connection) {
    try (Scope scope = parentSpan.makeCurrent()) {
      source.connect(connection);
    }
  }

  @Override
  protected void subscribeActual(final Observer<? super T> observer) {
    try (Scope scope = parentSpan.makeCurrent()) {
      source.subscribe(new TracingObserver<>(observer, parentSpan));
    }
  }
}
