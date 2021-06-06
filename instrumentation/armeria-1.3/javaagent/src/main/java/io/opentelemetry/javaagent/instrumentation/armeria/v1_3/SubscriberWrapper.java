/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.armeria.v1_3;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class SubscriberWrapper implements Subscriber<Object> {
  private final Subscriber<Object> delegate;
  private final Context context;

  private SubscriberWrapper(Subscriber<Object> delegate, Context context) {
    this.delegate = delegate;
    this.context = context;
  }

  public static Subscriber<Object> wrap(Subscriber<Object> delegate) {
    Context context = Context.current();
    if (context != Context.root()) {
      return new SubscriberWrapper(delegate, context);
    }
    return delegate;
  }

  @Override
  public void onSubscribe(Subscription subscription) {
    try (Scope ignored = context.makeCurrent()) {
      delegate.onSubscribe(subscription);
    }
  }

  @Override
  public void onNext(Object o) {
    try (Scope ignored = context.makeCurrent()) {
      delegate.onNext(o);
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
