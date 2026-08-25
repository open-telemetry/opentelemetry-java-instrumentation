/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava.v1_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import rx.Observable;
import rx.OpenTelemetryTracingUtil;
import rx.Subscriber;

public final class TracedOnSubscribe<T, REQUEST> implements Observable.OnSubscribe<T> {
  private final Observable.OnSubscribe<T> delegate;
  private final Instrumenter<REQUEST, ?> instrumenter;
  private final Supplier<REQUEST> requestFactory;
  private final Context parentContext;

  /**
   * Traces every subscription with an operation of its own, taken from {@code requestFactory}.
   *
   * <p>An observable can be subscribed to more than once, and each subscription runs the operation
   * again. Handing every subscription its own operation keeps whatever one of them records, such as
   * the node it reached, out of the spans of the others.
   */
  public static <T, REQUEST> TracedOnSubscribe<T, REQUEST> perSubscription(
      Observable<T> originalObservable,
      Instrumenter<REQUEST, ?> instrumenter,
      Supplier<REQUEST> requestFactory) {
    return new TracedOnSubscribe<>(originalObservable, instrumenter, requestFactory);
  }

  public TracedOnSubscribe(
      Observable<T> originalObservable, Instrumenter<REQUEST, ?> instrumenter, REQUEST request) {
    this(originalObservable, instrumenter, () -> request);
  }

  private TracedOnSubscribe(
      Observable<T> originalObservable,
      Instrumenter<REQUEST, ?> instrumenter,
      Supplier<REQUEST> requestFactory) {
    delegate = OpenTelemetryTracingUtil.extractOnSubscribe(originalObservable);
    this.instrumenter = instrumenter;
    this.requestFactory = requestFactory;
    parentContext = Context.current();
  }

  @Override
  public void call(Subscriber<? super T> subscriber) {
    /*
    TODO: can't really call shouldStart() - couchbase async instrumentation nests CLIENT calls
    which normally should happen in a sequence
    InstrumentationTypes to the rescue?

    if (!instrumenter.shouldStart(parentContext, request)) {
      delegate.call(subscriber);
      return;
    }
     */

    REQUEST request = requestFactory.get();
    Context context = instrumenter.start(parentContext, request);
    AtomicReference<Context> contextRef = new AtomicReference<>(context);
    try (Scope ignored = context.makeCurrent()) {
      delegate.call(new TracedSubscriber<>(subscriber, instrumenter, contextRef, request));
    } catch (Throwable t) {
      Context spanContext = contextRef.getAndSet(null);
      if (spanContext != null) {
        instrumenter.end(spanContext, request, null, t);
      }
      throw t;
    }
  }
}
