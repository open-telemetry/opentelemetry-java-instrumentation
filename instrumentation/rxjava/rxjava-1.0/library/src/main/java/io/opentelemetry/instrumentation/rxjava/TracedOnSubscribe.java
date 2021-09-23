/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import rx.Observable;
import rx.Subscriber;
import rx.__OpenTelemetryTracingUtil;

public final class TracedOnSubscribe<T, REQUEST> implements Observable.OnSubscribe<T> {
  private final Observable.OnSubscribe<T> delegate;
  private final Instrumenter<REQUEST, ?> instrumenter;
  private final REQUEST request;
  private final Context parentContext;

  public TracedOnSubscribe(
      Observable<T> originalObservable, Instrumenter<REQUEST, ?> instrumenter, REQUEST request) {
    delegate = __OpenTelemetryTracingUtil.extractOnSubscribe(originalObservable);
    this.instrumenter = instrumenter;
    this.request = request;

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

    Context context = instrumenter.start(parentContext, request);
    try (Scope ignored = context.makeCurrent()) {
      delegate.call(new TracedSubscriber<>(subscriber, instrumenter, context, request));
    }
  }
}
