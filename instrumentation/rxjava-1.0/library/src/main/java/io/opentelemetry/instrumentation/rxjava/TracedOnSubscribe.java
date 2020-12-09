/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.BaseInstrumenter;
import rx.Observable;
import rx.Subscriber;
import rx.__OpenTelemetryTracingUtil;

public class TracedOnSubscribe<T> implements Observable.OnSubscribe<T> {
  private final Observable.OnSubscribe<T> delegate;
  private final String operationName;
  private final Context parentContext;
  private final BaseInstrumenter tracer;
  private final Span.Kind spanKind;

  public TracedOnSubscribe(
      Observable<T> originalObservable,
      String operationName,
      BaseInstrumenter tracer,
      Span.Kind spanKind) {
    delegate = __OpenTelemetryTracingUtil.extractOnSubscribe(originalObservable);
    this.operationName = operationName;
    this.tracer = tracer;
    this.spanKind = spanKind;

    parentContext = Context.current();
  }

  @Override
  public void call(Subscriber<? super T> subscriber) {
    // TODO pass Context into Tracer.startSpan() and then don't need this outer scoping
    try (Scope ignored = parentContext.makeCurrent()) {
      Context context = tracer.startOperation(operationName, spanKind);
      decorateSpan(Span.fromContext(context));
      try (Scope ignored1 = context.makeCurrent()) {
        delegate.call(new TracedSubscriber<>(Context.current(), subscriber, tracer));
      }
    }
  }

  protected void decorateSpan(Span span) {
    // Subclasses can use it to provide addition attributes to the span
  }
}
