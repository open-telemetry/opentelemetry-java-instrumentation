/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public final class TracingSubscriber<T> implements Subscriber<T> {
  private final Subscriber<T> delegate;
  private final Context context;

  public TracingSubscriber(Subscriber<T> delegate, Context context) {
    this.delegate = delegate;
    this.context = context;
  }

  @Override
  public void onSubscribe(Subscription subscription) {
    try (Scope ignore = context.makeCurrent()) {
      delegate.onSubscribe(subscription);
    }
  }

  @Override
  public void onNext(T t) {
    try (Scope ignore = context.makeCurrent()) {
      delegate.onNext(t);
    }
  }

  @Override
  public void onError(Throwable throwable) {
    try (Scope ignore = context.makeCurrent()) {
      delegate.onError(throwable);
    }
  }

  @Override
  public void onComplete() {
    try (Scope ignore = context.makeCurrent()) {
      delegate.onComplete();
    }
  }
}
