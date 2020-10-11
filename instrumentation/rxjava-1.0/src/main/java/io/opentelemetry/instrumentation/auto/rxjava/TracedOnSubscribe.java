/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.rxjava;

import io.grpc.Context;
import io.opentelemetry.context.ContextUtils;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.trace.Span;
import rx.Observable;
import rx.Subscriber;
import rx.__OpenTelemetryTracingUtil;

public class TracedOnSubscribe<T> implements Observable.OnSubscribe<T> {
  private final Observable.OnSubscribe<?> delegate;
  private final String operationName;
  private final Context parentContext;
  private final BaseTracer tracer;
  private final Span.Kind spanKind;

  public TracedOnSubscribe(
      Observable originalObservable, String operationName, BaseTracer tracer, Span.Kind spanKind) {
    delegate = __OpenTelemetryTracingUtil.extractOnSubscribe(originalObservable);
    this.operationName = operationName;
    this.tracer = tracer;
    this.spanKind = spanKind;

    parentContext = Context.current();
  }

  @Override
  public void call(Subscriber<? super T> subscriber) {
    // TODO pass Context into Tracer.startSpan() and then don't need this outer scoping
    try (Scope ignored = ContextUtils.withScopedContext(parentContext)) {
      Span span = tracer.startSpan(operationName, spanKind);
      decorateSpan(span);
      try (Scope ignored1 = tracer.startScope(span)) {
        delegate.call(new TracedSubscriber(Context.current(), subscriber, tracer));
      }
    }
  }

  protected void decorateSpan(Span span) {
    // Subclasses can use it to provide addition attributes to the span
  }
}
