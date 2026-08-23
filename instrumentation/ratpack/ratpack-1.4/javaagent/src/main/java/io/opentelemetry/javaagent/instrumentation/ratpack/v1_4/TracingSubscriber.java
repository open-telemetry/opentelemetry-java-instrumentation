/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack.v1_4;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class TracingSubscriber<T> implements Subscriber<T> {
  private final Subscriber<T> delegate;
  private final Context context;

  public TracingSubscriber(Subscriber<T> delegate, Context context) {
    this.delegate = delegate;
    this.context = context;
  }

  @Override
  public void onSubscribe(Subscription subscription) {
    try (Scope ignored = context.makeCurrent()) {
      delegate.onSubscribe(subscription);
    }
  }

  @Override
  public void onNext(T t) {
    try (Scope ignored = context.makeCurrent()) {
      delegate.onNext(t);
    }
  }

  @Override
  public void onError(Throwable throwable) {
    try (Scope ignored = context.makeCurrent()) {
      delegate.onError(throwable);
    }
  }

  @Override
  public void onComplete() {
    try (Scope ignored = context.makeCurrent()) {
      delegate.onComplete();
    }
  }
}
