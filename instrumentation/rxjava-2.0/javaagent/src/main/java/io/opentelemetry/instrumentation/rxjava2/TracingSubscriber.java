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
  private final Context parentSpan;

  public TracingSubscriber(final Subscriber<T> subscriber, final Context parentSpan) {
    this.subscriber = subscriber;
    this.parentSpan = parentSpan;
  }

  @Override
  public void onSubscribe(final Subscription subscription) {
    subscriber.onSubscribe(subscription);
  }

  @Override
  public void onNext(final T t) {
    try (final Scope scope = parentSpan.makeCurrent()) {
      subscriber.onNext(t);
    }
  }

  @Override
  public void onError(final Throwable throwable) {
    try (final Scope scope = parentSpan.makeCurrent()) {
      subscriber.onError(throwable);
    }
  }

  @Override
  public void onComplete() {
    try (final Scope scope = parentSpan.makeCurrent()) {
      subscriber.onComplete();
    }
  }
}
