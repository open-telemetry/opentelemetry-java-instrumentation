/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor;

import io.opentelemetry.context.ContextUtils;
import io.opentelemetry.context.Scope;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.util.context.Context;

/**
 * Based on OpenTracing code.
 * https://github.com/opentracing-contrib/java-reactor/blob/master/src/main/java/io/opentracing/contrib/reactor/TracedSubscriber.java
 */
public class TracingSubscriber<T> implements CoreSubscriber<T> {
  private final io.grpc.Context traceContext;
  private final Subscriber<? super T> subscriber;
  private final Context context;

  public TracingSubscriber(Subscriber<? super T> subscriber, Context ctx) {
    this(subscriber, ctx, io.grpc.Context.current());
  }

  public TracingSubscriber(
      Subscriber<? super T> subscriber, Context ctx, io.grpc.Context contextToPropagate) {
    this.subscriber = subscriber;
    this.traceContext = contextToPropagate;
    this.context = ctx;
  }

  @Override
  public void onSubscribe(Subscription subscription) {
    subscriber.onSubscribe(subscription);
  }

  @Override
  public void onNext(T o) {
    withActiveSpan(() -> subscriber.onNext(o));
  }

  @Override
  public void onError(Throwable throwable) {
    withActiveSpan(() -> subscriber.onError(throwable));
  }

  @Override
  public void onComplete() {
    withActiveSpan(subscriber::onComplete);
  }

  @Override
  public Context currentContext() {
    return context;
  }

  private void withActiveSpan(Runnable runnable) {
    if (traceContext != null) {
      try (Scope ignored = ContextUtils.withScopedContext(traceContext)) {
        runnable.run();
      }
    } else {
      runnable.run();
    }
  }
}
