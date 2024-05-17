/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.armeria.v1_3;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class SubscriberWrapper<T> implements Subscriber<T> {
  private static final Class<?> abortingSubscriberClass = getAbortingSubscriberClass();
  private static final Class<?> noopSubscriberClass = getNoopSubscriberClass();

  private final Subscriber<T> delegate;
  private final Context context;

  private static Class<?> getAbortingSubscriberClass() {
    // AbortingSubscriber is package private
    try {
      return Class.forName("com.linecorp.armeria.common.stream.AbortingSubscriber");
    } catch (ClassNotFoundException exception) {
      return null;
    }
  }

  private static Class<?> getNoopSubscriberClass() {
    // NoopSubscriber is package private
    try {
      return Class.forName("com.linecorp.armeria.common.stream.NoopSubscriber");
    } catch (ClassNotFoundException exception) {
      return null;
    }
  }

  private SubscriberWrapper(Subscriber<T> delegate, Context context) {
    this.delegate = delegate;
    this.context = context;
  }

  private static <T> boolean isIgnored(Subscriber<T> delegate) {
    return (abortingSubscriberClass != null && abortingSubscriberClass.isInstance(delegate))
        || (noopSubscriberClass != null && noopSubscriberClass.isInstance(delegate));
  }

  public static <T> Subscriber<T> wrap(Subscriber<T> delegate) {
    if (isIgnored(delegate)) {
      return delegate;
    }

    Context context = Context.current();
    if (context != Context.root()) {
      return new SubscriberWrapper<>(delegate, context);
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
  public void onNext(T o) {
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
