/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava2;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public final class TracingSubscriber<T> implements Subscriber<T> {

  private final Subscriber<T> subscriber;
  private final Context context;

  public TracingSubscriber(final Subscriber<T> subscriber, final Context context) {
    this.subscriber = subscriber;
    this.context = context;
  }

  @Override
  public void onSubscribe(final Subscription subscription) {
    subscriber.onSubscribe(subscription);
  }

  @Override
  public void onNext(final T t) {
    try (Scope ignored = context.makeCurrent()) {
      subscriber.onNext(t);
    }
  }

  @Override
  public void onError(final Throwable throwable) {
    try (Scope ignored = context.makeCurrent()) {
      subscriber.onError(throwable);
    }
  }

  @Override
  public void onComplete() {
    try (Scope ignored = context.makeCurrent()) {
      subscriber.onComplete();
    }
  }
}
