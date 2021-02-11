/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava2;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.reactivex.internal.fuseable.QueueSubscription;
import io.reactivex.internal.subscribers.BasicFuseableSubscriber;
import org.reactivestreams.Subscriber;

class TracingSubscriber<T> extends BasicFuseableSubscriber<T, T> {

  private final Context parentSpan;

  TracingSubscriber(final Subscriber<? super T> actual, final Context parentSpan) {
    super(actual);
    this.parentSpan = parentSpan;
  }

  @Override
  public void onNext(T t) {
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
    final QueueSubscription<T> qs = this.qs;
    if (qs != null) {
      final int m = qs.requestFusion(mode);
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
