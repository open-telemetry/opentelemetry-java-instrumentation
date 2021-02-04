/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava2;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.reactivex.internal.fuseable.ConditionalSubscriber;
import io.reactivex.internal.fuseable.QueueSubscription;
import io.reactivex.internal.subscribers.BasicFuseableConditionalSubscriber;

public class TracingConditionalSubscriber<T> extends BasicFuseableConditionalSubscriber<T, T> {

  private final Context parentSpan;

  TracingConditionalSubscriber(
      final ConditionalSubscriber<? super T> actual, final Context parentSpan) {
    super(actual);
    this.parentSpan = parentSpan;
  }

  @Override
  public boolean tryOnNext(T t) {
    try (Scope scope = parentSpan.makeCurrent()) {
      return actual.tryOnNext(t);
    }
  }

  @Override
  public void onNext(T t) {
    try (Scope scope = parentSpan.makeCurrent()) {
      actual.onNext(t);
    }
  }

  @Override
  public void onError(Throwable t) {
    try (Scope scope = parentSpan.makeCurrent()) {
      actual.onError(t);
    }
  }

  @Override
  public void onComplete() {
    try (Scope scope = parentSpan.makeCurrent()) {
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
